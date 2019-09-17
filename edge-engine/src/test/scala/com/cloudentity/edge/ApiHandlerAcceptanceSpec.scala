package com.cloudentity.edge

import io.circe.Json
import com.cloudentity.edge.api.{ApiHandlerVerticle, ApiServer, RoutingCtxVerticle}
import com.cloudentity.edge.apigroup.{ApiGroup, ApiGroupConf, ApiGroupsStore}
import com.cloudentity.edge.config.Conf.AppConf
import com.cloudentity.edge.domain.flow._
import com.cloudentity.edge.domain.http
import com.cloudentity.edge.domain.http.ApiResponse
import com.cloudentity.edge.domain.rule.RuleConf
import com.cloudentity.edge.plugin.PluginFunctions.RequestPlugin
import com.cloudentity.edge.rule.Rule
import com.cloudentity.edge.util.MockUtils
import com.cloudentity.tools.vertx.conf.fixed.FixedConfVerticle
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.http.Headers
import com.cloudentity.tools.vertx.test.ScalaVertxUnitTest
import com.cloudentity.tools.vertx.verticles.VertxDeploy
import io.restassured.RestAssured
import io.restassured.RestAssured._
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.{ReplyException, ReplyFailure}
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.core.{Future => VxFuture}
import io.vertx.ext.unit.TestContext
import org.hamcrest.core.StringContains
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpRequest

import scala.concurrent.Future

class ApiHandlerAcceptanceSpec extends ScalaVertxUnitTest with MockUtils {
  @Test
  def shouldApplyRewritePathWithoutQuery(ctx: TestContext): Unit = {
    val apiGroup = overrideRuleConf(_.copy(rewritePath = Some(RewritePath("/other-path")), copyQueryOnRewrite = Some(true)))
    mockOnPath(targetService)("/other-path", resp().withStatusCode(200))

    deployAndCall(ctx, apiGroup) {
      given()
        .when()
      .get("/path")
        .`then`()
        .statusCode(200)
    }
  }

  @Test
  def shouldApplyRewritePathAndCopyQuery(ctx: TestContext): Unit = {
    val apiGroup = overrideRuleConf(_.copy(rewritePath = Some(RewritePath("/other-path")), copyQueryOnRewrite = Some(true)))
    targetService.when(req().withPath("/other-path")).callback { request: HttpRequest =>
      if (request.hasQueryStringParameter("q", "1")) resp().withStatusCode(200)
      else resp().withStatusCode(404)
    }

    deployAndCall(ctx, apiGroup) {
      given()
      .when()
        .get("/path?q=1")
      .`then`()
        .statusCode(200)
    }
  }

  @Test
  def shouldApplyRewritePathAndDropQuery(ctx: TestContext): Unit = {
    val apiGroup = overrideRuleConf(_.copy(rewritePath = Some(RewritePath("/other-path")), copyQueryOnRewrite = Some(false)))
    targetService.when(req().withPath("/other-path")).callback { request: HttpRequest =>
      if (!request.hasQueryStringParameter("q", "1")) resp().withStatusCode(200)
      else resp().withStatusCode(404)
    }

    deployAndCall(ctx, apiGroup) {
      given()
      .when()
        .get("/path?q=1")
      .`then`()
        .statusCode(200)
    }
  }

  @Test
  def shouldApplyRewriteMethod(ctx: TestContext): Unit = {
    val apiGroup = overrideRuleConf(_.copy(rewriteMethod = Some(RewriteMethod(HttpMethod.POST))))
    targetService.when(req().withMethod("POST")).callback { _ =>
      resp().withStatusCode(200)
    }

    deployAndCall(ctx, apiGroup) {
      given()
      .when()
        .get("/path")
      .`then`()
        .statusCode(200)
    }
  }

  @Test
  def shouldModifyResponseIfTargetServiceCalled(ctx: TestContext): Unit = {
    val modify: RequestPlugin = req =>
      Future.successful(req.withModifyResponse(_.copy(headers = Headers("header" -> List("value")))))

    val apiGroup = overrideRule(_.copy(requestPlugins = List(modify)))

    targetService.when(req().withPath("/path")).callback { _ =>
      resp().withStatusCode(200)
    }

    deployAndCall(ctx, apiGroup) {
      given()
      .when()
        .get("/path")
      .`then`()
        .statusCode(200)
        .header("header", "value")
    }
  }

  @Test
  def shouldModifyResponseIfPluginReturnedResponse(ctx: TestContext): Unit = {
    val modify: RequestPlugin = req =>
      Future.successful(req.withModifyResponse(_.copy(headers = Headers("header" -> List("value")))))
    val respond: RequestPlugin = req =>
      Future.successful(req.abort(ApiResponse(200, Buffer.buffer(), Headers())))

    val apiGroup = overrideRule(_.copy(requestPlugins = List(modify, respond)))

    deployAndCall(ctx, apiGroup) {
      given()
      .when()
        .get("/path")
      .`then`()
        .statusCode(200)
        .header("header", "value")
    }
  }

  @Test
  def shouldNotSetFormParamsAsQueryParamsIfFormContent(ctx: TestContext): Unit = {
    val respond: RequestPlugin = req =>
      Future.successful(req.abort(http.ApiResponse(200, Buffer.buffer(), Headers("querySize" -> List(req.request.uri.query.toMap.size.toString)))))

    val rule = overrideRule(_.copy(requestPlugins = List(respond)).copy(conf = defaultRule.conf.copy(criteria = defaultRule.conf.criteria.copy(method = HttpMethod.POST))))

    deployAndCall(ctx, rule) {
      given()
        .header("Content-Type", "application/x-www-form-urlencoded")
        .body("token=eyJraWQiO&token_type_hint=access_token")
        .when()
      .post("/path")
      .`then`()
        .statusCode(200)
        .header("querySize", "0")
    }
  }

  @Test
  def shouldPreserveHostHeaderIfConfigured(ctx: TestContext): Unit = {
    targetService.when(req().withPath("/path")).callback { request: HttpRequest =>
      resp().withStatusCode(200).withHeader("Request-Host", request.getFirstHeader("Host"))
    }

    val apiGroup = overrideRule(_.copy(conf = defaultRule.conf.copy(preserveHostHeader = Some(true))))
    val hostHeader = "api-gateway.com"

    deployAndCall(ctx, apiGroup) {
      given()
        .header("Host", hostHeader)
      .when()
        .get("/path")
      .`then`()
        .statusCode(200)
        .header("Request-Host", hostHeader)
    }
  }

  @Test
  def shouldReplaceHostHeaderByDefault(ctx: TestContext): Unit = {
    targetService.when(req().withPath("/path")).callback { request: HttpRequest =>
      resp().withStatusCode(200).withHeader("Request-Host", request.getFirstHeader("Host"))
    }

    val apiGroup = overrideRule(_.copy(conf = defaultRule.conf.copy(preserveHostHeader = Some(false))))
    val hostHeader = "api-gateway.com"

    deployAndCall(ctx, apiGroup) {
      given()
        .header("Host", hostHeader)
        //.baseUri("localhost")
      .when()
        .get("/path")
      .`then`()
        .statusCode(200)
        .header("Request-Host", s"localhost:${targetPort}")
    }
  }

  @Test
  def shouldMatchBasePath(ctx: TestContext): Unit = {
    val apiGroup = ApiGroup(GroupMatchCriteria(Some(BasePath("/base-path")), None), List(defaultRule))
    mockOnPath(targetService)("/path", resp().withStatusCode(200))

    deployAndCall(ctx, apiGroup) {
      given()
      .when()
        .get("/base-path/path")
      .`then`()
        .statusCode(200)
    }
  }

  @Test
  def shouldMatchDomain(ctx: TestContext): Unit = {
    val apiGroup = ApiGroup(GroupMatchCriteria(None, Some(List(DomainPattern("example.com")))), List(defaultRule))
    mockOnPath(targetService)("/path", resp().withStatusCode(200))

    deployAndCall(ctx, apiGroup) {
      given()
        .header("Host", "example.com")
      .when()
        .get("/path")
      .`then`()
        .statusCode(200)
    }
  }

  @Test
  def shouldMatchSubDomain(ctx: TestContext): Unit = {
    val apiGroup = ApiGroup(GroupMatchCriteria(None, Some(List(DomainPattern("*.com")))), List(defaultRule))
    mockOnPath(targetService)("/path", resp().withStatusCode(200))

    deployAndCall(ctx, apiGroup) {
      given()
        .header("Host", "example.com")
        .when()
        .get("/path")
        .`then`()
        .statusCode(200)
    }
  }

  @Test
  def shouldMatchSubSubDomain(ctx: TestContext): Unit = {
    val apiGroup = ApiGroup(GroupMatchCriteria(None, Some(List(DomainPattern("*.*.com")))), List(defaultRule))
    mockOnPath(targetService)("/path", resp().withStatusCode(200))

    deployAndCall(ctx, apiGroup) {
      given()
        .header("Host", "x.example.com")
        .when()
        .get("/path")
        .`then`()
        .statusCode(200)
    }
  }

  @Test
  def shouldNotMatchSubSubDomain(ctx: TestContext): Unit = {
    val apiGroup = ApiGroup(GroupMatchCriteria(None, Some(List(DomainPattern("*.com")))), List(defaultRule))
    mockOnPath(targetService)("/path", resp().withStatusCode(200))

    deployAndCall(ctx, apiGroup) {
      given()
        .header("Host", "x.example.com")
        .when()
        .get("/path")
        .`then`()
        .statusCode(404)
    }
  }

  @Test
  def shouldNotMatchIfBasePathMatchesAndDomainDoesNot(ctx: TestContext): Unit = {
    val apiGroup = ApiGroup(GroupMatchCriteria(Some(BasePath("/base-path")), None), List(defaultRule))
    mockOnPath(targetService)("/path", resp().withStatusCode(200))

    deployAndCall(ctx, apiGroup) {
      given()
      .when()
        .get("/base-path")
      .`then`()
        .statusCode(404)
    }
  }

  @Test
  def shouldNotMatchIfDomainMatchesAndBasePathDoesNot(ctx: TestContext): Unit = {
    val apiGroup = ApiGroup(GroupMatchCriteria(Some(BasePath("/base-path")), None), List(defaultRule))
    mockOnPath(targetService)("/path", resp().withStatusCode(200))

    deployAndCall(ctx, apiGroup) {
      given()
      .when()
        .get("/base-path")
      .`then`()
        .statusCode(404)
    }
  }

  @Test
  def shouldReturn504OnEventBusTimeout(ctx: TestContext): Unit = {
    val delay: RequestPlugin = req => Future.failed(new ReplyException(ReplyFailure.TIMEOUT, "Timed out"))

    val apiGroup = ApiGroup(GroupMatchCriteria(None, None), List(defaultRule.copy(requestPlugins = List(delay))))

    deployAndCall(ctx, apiGroup) {
      given()
      .when()
        .get("/path")
      .`then`()
        .statusCode(504)
        .body(StringContains.containsString("System.Timeout"))
    }
  }

  var targetService: ClientAndServer = null
  var targetPort = 9000
  var apigwPort = 8123
  val defaultRule = Rule(RuleConf(None, EndpointMatchCriteria(HttpMethod.GET, PathMatching("^/path$".r, Nil, PathPrefix(""), "")), StaticServiceRule(TargetHost("localhost"), 9000, false), true, None, None, None, None, Nil, None), Nil, Nil)

  RestAssured.port = apigwPort

  @Before
  def init() = {
    targetService = startClientAndServer(targetPort)
  }

  @After
  def cleanup() = {
    targetService.close()
  }

  private def overrideRuleConf(f: RuleConf => RuleConf): ApiGroup =
    ApiGroup(GroupMatchCriteria(None, None), List(defaultRule.copy(conf = f(defaultRule.conf))))

  private def overrideRule(f: Rule => Rule): ApiGroup =
    ApiGroup(GroupMatchCriteria(None, None), List(f(defaultRule)))

  private def deployAndCall(ctx: TestContext, apiGroup: ApiGroup)(call: => Unit): Unit = deployAndCall(ctx, List(apiGroup))(call)

  private def deployAndCall(ctx: TestContext, apiGroups: List[ApiGroup])(call: => Unit): Unit =
    deployApiHandler(apiGroups)
      .flatMap { _ =>
        call
        Future.successful(())
      }
      .toJava().setHandler(ctx.asyncAssertSuccess())

  private def deployApiHandler(apiGroups: List[ApiGroup]): Future[Unit] =
    for {
      _ <- FixedConfVerticle.deploy(vertx, new JsonObject()).toScala
      _ <- VertxDeploy.deploy(vertx, new TestApiGroupsStore(apiGroups)).toScala
      _ <- VertxDeploy.deploy(vertx, new RoutingCtxVerticle()).toScala
      _ <- VertxDeploy.deploy(vertx, new ApiHandlerVerticle).toScala
      _ <- VertxDeploy.deploy(vertx, new ApiServer(AppConf(Some(apigwPort), None, Some(Json.arr()), None, None, None, None, None, None, None))).toScala
    } yield ()

  private def resp() = org.mockserver.model.HttpResponse.response()
  private def req() = org.mockserver.model.HttpRequest.request()
}

class TestApiGroupsStore(apiGroups: List[ApiGroup]) extends ScalaServiceVerticle with ApiGroupsStore {
  override def getGroups(): VxFuture[List[ApiGroup]] = VxFuture.succeededFuture(apiGroups)
  override def getGroupConfs(): VxFuture[List[ApiGroupConf]] = VxFuture.succeededFuture(Nil)
}