package com.cloudentity.edge.plugin

import java.util.Optional

import com.cloudentity.services.authzservice.client.model.{Recovery, ValidationInvalid, ValidationInvalid_details}
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import com.cloudentity.edge.plugin.bus.request._
import com.cloudentity.edge.domain._
import com.cloudentity.edge.domain.flow.{AccessLogItems, AuthnCtx, PluginConf, PluginName, RequestCtx}
import com.cloudentity.edge.jwt.impl.SymmetricJwtService
import com.cloudentity.edge.plugin.impl.authz._
import com.cloudentity.edge.test.TestRequestResponseCtx
import com.cloudentity.edge.util.MockUtils
import com.cloudentity.tools.vertx.bus.ServiceClientFactory
import com.cloudentity.tools.vertx.conf.fixed.FixedConfVerticle
import com.cloudentity.tools.vertx.json.VertxJson
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.sd.SdVerticle
import com.cloudentity.tools.vertx.sd.provider.FixedSdProvider
import com.cloudentity.tools.vertx.http.Headers
import com.cloudentity.tools.vertx.test.VertxUnitTest
import com.cloudentity.tools.vertx.tracing.TracingContext
import com.cloudentity.tools.vertx.verticles.VertxDeploy
import io.vertx.core.json.{JsonArray, JsonObject}
import io.vertx.core.{DeploymentOptions, Future}
import io.vertx.ext.unit.TestContext
import org.junit.Assert.assertEquals
import org.junit.{After, Assert, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response

class AuthzPluginSpec extends VertxUnitTest with MockUtils with TestRequestResponseCtx {
  var authzService: ClientAndServer = _

  VertxJson.registerJsonObjectDeserializer()

  @Before
  def before(): Unit = {
    authzService = startClientAndServer(7777)
  }

  @After
  def after(): Unit = {
    authzService.stop()
  }

  val pluginConf =
    new JsonObject()
      .put("http", new JsonObject().put("serviceName", "authz"))
      .put("jwtServiceAddress", "symmetric")

  val sdProviderConf =
    new JsonObject().put("records",
      new JsonArray().add(
        new JsonObject()
          .put("name", "authz")
          .put("location",
            new JsonObject().put("port", 7777).put("host", "localhost").put("ssl", false)
          )
      )
    )

  val globalConf = new JsonObject()
    .put("plugin:authz", pluginConf)
    .put("fixed-sd-provider", sdProviderConf)
    .put("jwt", new JsonObject().put("secret", "x").put("expiresIn", "PT60S").put("issuer", "x"))

  val req = emptyRequestCtx
  val policyName = "POLICY_NAME"
  val validateGlobalPolicyPath = s"/policy/$policyName/validate"
  val applicationId = "APP_ID"
  val validateApplicationPolicyPath = s"/policy/$policyName/application/$applicationId/validate"

  val successAccessLog = AccessLogItems("authz" -> AccessLogItem(policyName, "success", None).asJson)
  val errorAccessLog = AccessLogItems("authz" -> AccessLogItem(policyName, "error", None).asJson)
  def failureAccessLog(recovery: Option[Boolean]) = AccessLogItems("authz" -> AccessLogItem(policyName, "failure", recovery).asJson)

  @Test
  def shouldForwardWhenCallingGlobalPolicyReturns200(ctx: TestContext): Unit = {
    mockOnPath(authzService)(validateGlobalPolicyPath, response().withStatusCode(200))

    deploy(globalConf)
      .compose(apply(AuthzPluginConf(policyName, None, None)))
      .setHandler(
        ctx.asyncAssertSuccess {
          assertResponse {
            case Continue(ctx) =>
              assertEquals(successAccessLog, ctx.accessLog)
          }
        }
      )
  }

  @Test
  def shouldForwardWhenCallingApplicationPolicyReturns200(ctx: TestContext): Unit = {
    mockOnPath(authzService)(validateApplicationPolicyPath, response().withStatusCode(200))

    deploy(globalConf)
      .compose(apply(AuthzPluginConf(policyName, None, Some(applicationId))))
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
  def shouldPassSingleTargetEntityToAuthz(ctx: TestContext): Unit = {
    mockOnPath(authzService)(validateApplicationPolicyPath, response().withStatusCode(200))

    val appJson = Json.obj("name" -> Json.fromString("My App"))
    val entityProviders = new JsonObject().put("plugin:authz", new JsonObject().put("entityProviders", new JsonObject().put("application", "applicationProvider")))
    val conf = AuthzPluginConf(policyName, None, Some(applicationId), Some(List(TargetEntity(HeaderEntityId("appId"), EntityType("application"), None))))

    class MockApplicationClientVerticle extends ScalaServiceVerticle with TargetEntityProvider {
      override def vertxServiceAddressPrefixS: Option[String] = Some("applicationProvider")
      override def getEntity(ctx: TracingContext, uuid: String): Future[Json] = Future.succeededFuture(appJson)
    }

    VertxDeploy.deploy(vertx, new MockApplicationClientVerticle)
      .compose(_ => deploy(globalConf.mergeIn(entityProviders, true)))
      .compose(apply(conf, req.modifyRequest(_.copy(headers = Headers("appId" -> List("APP_ID"))))))
      .setHandler(
        ctx.asyncAssertSuccess {
          assertResponse {
            case Continue(requestCtx) =>
              ctx.assertEquals(AuthnCtx("targets" -> Json.obj("application" -> appJson)), requestCtx.authnCtx)
              assertEquals(successAccessLog, requestCtx.accessLog)
          }
        }
      )
  }

  @Test
  def shouldPassMultipleTargetEntityToAuthz(ctx: TestContext): Unit = {
    mockOnPath(authzService)(validateApplicationPolicyPath, response().withStatusCode(200))

    val app1Json = Json.obj("name" -> Json.fromString("My App 1"))
    val app2Json = Json.obj("name" -> Json.fromString("My App 2"))
    val entityProviders = new JsonObject().put("plugin:authz", new JsonObject().put("entityProviders", new JsonObject().put("application", "applicationProvider")))
    val targetEntities = List(
      TargetEntity(HeaderEntityId("appId1"), EntityType("application"), None),
      TargetEntity(HeaderEntityId("appId2"), EntityType("application"), Some("otherApplication"))
    )
    val conf = AuthzPluginConf(policyName, None, Some(applicationId), Some(targetEntities))

    class MockApplicationClientVerticle extends ScalaServiceVerticle with TargetEntityProvider {
      override def vertxServiceAddressPrefixS: Option[String] = Some("applicationProvider")
      override def getEntity(ctx: TracingContext, uuid: String): Future[Json] =
        uuid match {
          case "APP_ID_1" => Future.succeededFuture(app1Json)
          case "APP_ID_2" => Future.succeededFuture(app2Json)
          case _          => Future.failedFuture("Could not find Application")
        }
    }

    VertxDeploy.deploy(vertx, new MockApplicationClientVerticle)
      .compose(_ => deploy(globalConf.mergeIn(entityProviders, true)))
      .compose(apply(conf, req.modifyRequest(_.copy(headers = Headers("appId1" -> List("APP_ID_1"), "appId2" -> List("APP_ID_2"))))))
      .setHandler(
        ctx.asyncAssertSuccess {
          assertResponse {
            case Continue(requestCtx) =>
              ctx.assertEquals(AuthnCtx("targets" -> Json.obj("application" -> app1Json, "otherApplication" -> app2Json)), requestCtx.authnCtx)
              assertEquals(successAccessLog, requestCtx.accessLog)
          }
        }
      )
  }

  @Test
  def shouldSendRequestPayloadIfConfigured(ctx: TestContext): Unit = {
    authzService.when(request().withPath(validateGlobalPolicyPath)).callback { request: HttpRequest =>
      if (request.getPath == validateGlobalPolicyPath)
        if (request.getBodyAsString.nonEmpty) response().withStatusCode(200)
        else response().withStatusCode(500)
      else response().withStatusCode(404)
    }

    deploy(globalConf)
      .compose(apply(AuthzPluginConf(policyName, Some(true), None)))
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
  def shouldRespond401WhenCallingGlobalPolicyReturns401(ctx: TestContext): Unit = {
    val invalidPolicyResponseBody = ValidationInvalid(None, None, Some(ValidationInvalid_details(Some(Seq(Recovery("recoveryType"))))))
    mockOnPath(authzService)(validateGlobalPolicyPath, response().withStatusCode(401).withBody(invalidPolicyResponseBody.asJson.toString))

    deploy(globalConf)
      .compose(apply(AuthzPluginConf(policyName, None, None)))
      .setHandler(
        ctx.asyncAssertSuccess {
          assertResponse {
            case Continue(requestCtx) if requestCtx.isAborted() =>
              assertEquals(401, requestCtx.aborted.get.statusCode)
              assertEquals(failureAccessLog(Some(true)), requestCtx.accessLog)
          }
        }
      )
  }

  @Test
  def shouldRespond403WhenCallingGlobalPolicyReturns403(ctx: TestContext): Unit = {
    mockOnPath(authzService)(validateGlobalPolicyPath, response().withStatusCode(403))

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
  def shouldRespond500WhenCallingGlobalPolicyReturnsNon200Or401Or403(ctx: TestContext): Unit = {
    mockOnPath(authzService)(validateGlobalPolicyPath, response().withStatusCode(404))

    deploy(globalConf)
      .compose(apply(AuthzPluginConf(policyName, None, None)))
      .setHandler(
        ctx.asyncAssertSuccess {
          assertResponse {
            case Continue(requestCtx) if requestCtx.isAborted() =>
              assertEquals(500, requestCtx.aborted.get.statusCode)
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
      .compose(_ => VertxDeploy.deploy(vertx(), new AuthzPlugin(), new DeploymentOptions().setConfig(new JsonObject().put("verticleId", "plugin:authz"))))
      .compose(_ => Future.succeededFuture(ServiceClientFactory.make(vertx().eventBus(), classOf[RequestPluginService], Optional.of("authz"))))
  }
}
