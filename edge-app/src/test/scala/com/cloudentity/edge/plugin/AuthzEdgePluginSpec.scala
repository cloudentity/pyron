package com.cloudentity.edge.plugin

import java.util.Optional

import io.circe.generic.auto._
import io.circe.syntax._
import com.cloudentity.edge.plugin.bus.request._
import com.cloudentity.edge.domain.flow.{AccessLogItems, PluginConf, PluginName, RequestCtx}
import com.cloudentity.edge.jwt.impl.SymmetricJwtService
import com.cloudentity.edge.plugin.impl.authz._
import com.cloudentity.edge.service.authz.SidecarResponse
import com.cloudentity.edge.test.TestRequestResponseCtx
import com.cloudentity.edge.util.MockUtils
import com.cloudentity.tools.vertx.bus.ServiceClientFactory
import com.cloudentity.tools.vertx.conf.fixed.FixedConfVerticle
import com.cloudentity.tools.vertx.json.VertxJson
import com.cloudentity.tools.vertx.sd.SdVerticle
import com.cloudentity.tools.vertx.sd.provider.FixedSdProvider
import com.cloudentity.tools.vertx.test.VertxUnitTest
import com.cloudentity.tools.vertx.verticles.VertxDeploy
import io.vertx.core.json.{JsonArray, JsonObject}
import io.vertx.core.{DeploymentOptions, Future}
import io.vertx.ext.unit.TestContext
import org.junit.Assert.assertEquals
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpResponse.response

class AuthzEdgePluginSpec extends VertxUnitTest with MockUtils with TestRequestResponseCtx {
  var s5dService: ClientAndServer = _

  VertxJson.registerJsonObjectDeserializer()

  @Before
  def before(): Unit = {
    s5dService = startClientAndServer(3001)
  }

  @After
  def after(): Unit = {
    s5dService.stop()
  }

  val pluginConf =
    new JsonObject()
    .put("securitySidecar", new JsonObject()
        .put("http", new JsonObject().put("serviceName", "s5d"))
    )
    .put("identityServiceAddress", "symmetric")
    .put("identityServiceHeader", "x-ce-identity")
    .put("fingerprintServiceAddress", "symmetric")
    .put("fingerprintServiceHeader", "x-ce-fingerprint")

  val sdProviderConf =
    new JsonObject().put("records",
      new JsonArray().add(
        new JsonObject()
          .put("name", "s5d")
          .put("location",
            new JsonObject().put("port", 3001).put("host", "localhost").put("ssl", false)
          )
      )
    )

  val globalConf = new JsonObject()
    .put("plugin:authz", pluginConf)
    .put("fixed-sd-provider", sdProviderConf)
    .put("jwt", new JsonObject().put("secret", "x").put("expiresIn", "PT60S").put("issuer", "x"))

  val req = emptyRequestCtx
  val policyName = "POLICY_NAME"
  val s5dValidate = "/v2/validatePolicy"
  val applicationId = "APP_ID"
  val validateApplicationPolicyPath = s"/policy/$policyName/application/$applicationId/validate"

  val successAccessLog = AccessLogItems("authz" -> AccessLogItem(policyName, "success", None).asJson)
  val errorAccessLog = AccessLogItems("authz" -> AccessLogItem(policyName, "error", None).asJson)
  def failureAccessLog(recovery: Option[Boolean]) = AccessLogItems("authz" -> AccessLogItem(policyName, "failure", recovery).asJson)

  @Test
  def shouldValidateInSecuritySidecar(ctx: TestContext): Unit = {
    mockOnPath(s5dService)(s5dValidate, response().withStatusCode(200).withBody("""{"result":"AUTHORIZED"}"""))

    deploy(globalConf)
      .compose(apply(AuthzPluginConf(policyName, None, None)))
      .setHandler(
        ctx.asyncAssertSuccess {
          assertResponse {
            case Continue(requestCtx) =>
              assertEquals(successAccessLog, requestCtx.accessLog)
          }
        }
      )
  }

  @Test
  def shouldFailWith403WhenSecuritySidecarRespondedWithNoValidator(ctx: TestContext): Unit = {
    mockOnPath(s5dService)(s5dValidate, response().withStatusCode(200).withBody("""{ "result": "NO_VALIDATOR"}"""))

    deploy(globalConf)
      .compose(apply(AuthzPluginConf(policyName, None, None)))
      .setHandler(
        ctx.asyncAssertSuccess {
          assertResponse {
            case Continue(requestCtx) if requestCtx.isAborted() =>
              assertEquals(403, requestCtx.aborted.get.statusCode)
              assertEquals(errorAccessLog, requestCtx.accessLog)
          }
        }
      )
  }

  @Test
  def shouldForwardWhenSidecarReturnedAuthorized(ctx: TestContext): Unit = {
    mockOnPath(s5dService)(s5dValidate, response().withStatusCode(200).withBody(SidecarResponse("AUTHORIZED", None, None).asJson.toString()))

    deploy(globalConf)
      .compose(apply(AuthzPluginConf(policyName, None, None)))
      .setHandler(
        ctx.asyncAssertSuccess {
          assertResponse {
            case Continue(requestCtx) =>
              assertEquals(successAccessLog, requestCtx.accessLog)
          }
        }
      )
  }

  @Test
  def shouldRespond403WhenSidecarReturnedNotAuthorized(ctx: TestContext): Unit = {
    mockOnPath(s5dService)(s5dValidate, response().withStatusCode(200).withBody(SidecarResponse("NOT_AUTHORIZED", None, None).asJson.toString()))

    deploy(globalConf)
      .compose(apply(AuthzPluginConf(policyName, None, None)))
      .setHandler(
        ctx.asyncAssertSuccess {
          assertResponse {
            case Continue(requestCtx) if requestCtx.isAborted() =>
              assertEquals(403, requestCtx.aborted.get.statusCode)
              assertEquals(failureAccessLog(None), requestCtx.accessLog)
          }
        }
      )
  }

  @Test
  def shouldRespond403WhenCallingGlobalPolicyReturnsNon200Or401Or403(ctx: TestContext): Unit = {
    mockOnPath(s5dService)(s5dValidate, response().withStatusCode(404))

    deploy(globalConf)
      .compose(apply(AuthzPluginConf(policyName, None, None)))
      .setHandler(
        ctx.asyncAssertSuccess {
          assertResponse {
            case Continue(requestCtx) if requestCtx.isAborted() =>
              assertEquals(403, requestCtx.aborted.get.statusCode)
              assertEquals(errorAccessLog, requestCtx.accessLog)
          }
        }
      )
  }

  private def assertResponse(a: PartialFunction[ApplyResponse, Unit])(response: ApplyResponse): Unit =
    if (a.isDefinedAt(response)) a.apply(response)
    else                         throw new Exception(s"unexpected ApplyResponse: $response")

  private def apply(conf: AuthzPluginConf)(plugin: RequestPluginService): Future[ApplyResponse] =
    apply(conf, req)(plugin)

  private def apply(conf: AuthzPluginConf, ctx: RequestCtx)(plugin: RequestPluginService): Future[ApplyResponse] =
    plugin.applyPlugin(ctx.tracingCtx, ApplyRequest(ctx, PluginConf(PluginName("authz"), conf.asJson)))

  private def deploy(globalConf: JsonObject): Future[RequestPluginService] = {
    FixedConfVerticle.deploy(vertx(), globalConf)
      .compose(_ => VertxDeploy.deploy(vertx(), new SdVerticle()))
      .compose(_ => VertxDeploy.deploy(vertx(), new FixedSdProvider()))
      .compose(_ => VertxDeploy.deploy(vertx(), new SymmetricJwtService(), new DeploymentOptions().setConfig(new JsonObject().put("verticleId", "jwt"))))
      .compose(_ => VertxDeploy.deploy(vertx(), new AuthzEdgePlugin(), new DeploymentOptions().setConfig(new JsonObject().put("verticleId", "plugin:authz"))))
      .compose(_ => Future.succeededFuture(ServiceClientFactory.make(vertx().eventBus(), classOf[RequestPluginService], Optional.of("authz"))))
  }
}
