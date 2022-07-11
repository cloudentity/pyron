package com.cloudentity.pyron.acceptance

import com.cloudentity.pyron.PyronAcceptanceTest
import com.cloudentity.pyron.domain.flow.{PluginName, RequestCtx, ResponseCtx}
import com.cloudentity.pyron.plugin.config.ValidateResponse
import com.cloudentity.pyron.plugin.verticle.{RequestPluginVerticle, ResponsePluginVerticle}
import com.cloudentity.pyron.util.MockUtils
import io.circe.Decoder
import io.restassured.RestAssured._
import io.vertx.core.eventbus.{ReplyException, ReplyFailure}
import io.vertx.ext.unit.TestContext
import org.hamcrest.core.StringContains
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}

import scala.concurrent.Future

class ApiHandlerRulesAcceptanceTest extends PyronAcceptanceTest with MockUtils {
  override def getMetaConfPath = "src/test/resources/acceptance/api-handler/meta-config-rules.json"

  def rulesTestBasePath = ""

  var targetService: ClientAndServer = _

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
  }

  @After
  def finish(): Unit = {
    targetService.stop
  }

  @Test
  def shouldApplyRewritePathWithoutQuery(ctx: TestContext): Unit = {
    mockOnPath(targetService)("/should-apply-rewrite-path-without-query-rewrite", resp().withStatusCode(200))

    given()
    .when()
      .get(rulesTestBasePath + "/should-apply-rewrite-path-without-query")
    .`then`()
      .statusCode(200)
  }

  @Test
  def shouldApplyRewritePathAndCopyQuery(ctx: TestContext): Unit = {
    targetService.when(req().withPath("/should-apply-rewrite-path-and-copy-query-rewrite")).respond { request: HttpRequest =>
      if (request.hasQueryStringParameter("q", "1")) resp().withStatusCode(200)
      else resp().withStatusCode(400)
    }

    given()
    .when()
      .get(rulesTestBasePath + "/should-apply-rewrite-path-and-copy-query?q=1")
    .`then`()
      .statusCode(200)
  }
  @Test
  def shouldApplyRewritePathAndDropQuery(ctx: TestContext): Unit = {
    targetService.when(req().withPath("/should-apply-rewrite-path-and-drop-query-rewrite")).respond { request: HttpRequest =>
      if (!request.hasQueryStringParameter("q", "1")) resp().withStatusCode(200)
      else resp().withStatusCode(400)
    }

    given()
    .when()
      .get(rulesTestBasePath + "/should-apply-rewrite-path-and-drop-query?q=1")
    .`then`()
      .statusCode(200)
  }

  @Test
  def shouldApplyRewriteMethod(ctx: TestContext): Unit = {
    targetService.when(req().withPath("/should-apply-rewrite-method").withMethod("POST")).respond { _: HttpRequest =>
      resp().withStatusCode(200)
    }

    given()
    .when()
      .get(rulesTestBasePath + "/should-apply-rewrite-method")
    .`then`()
      .statusCode(200)
  }

  @Test
  def shouldNotSetFormParamsAsQueryParamsIfFormContent(ctx: TestContext): Unit = {
    targetService.when(req()
      .withPath("/should-not-set-form-params-as-query-params-if-form-content")
      .withMethod("POST"))
      .respond { request: HttpRequest =>
        if (request.getQueryStringParameterList.isEmpty) resp().withStatusCode(200)
        else resp().withStatusCode(400)
      }

    given()
      .header("Content-Type", "application/x-www-form-urlencoded")
      .body("token=eyJraWQiO&token_type_hint=access_token")
    .when()
      .post(rulesTestBasePath + "/should-not-set-form-params-as-query-params-if-form-content")
    .`then`()
      .statusCode(200)
  }

  @Test
  def shouldPreserveHostHeaderIfConfigured(ctx: TestContext): Unit = {
    targetService.when(req().withPath("/should-preserve-host-header-if-configured")).respond { request: HttpRequest =>
      resp().withStatusCode(200).withHeader("Request-Host", request.getFirstHeader("Host"))
    }

    val hostHeader = "cloudentity.com"

    given()
      .header("Host", hostHeader)
    .when()
      .get(rulesTestBasePath + "/should-preserve-host-header-if-configured")
    .`then`()
      .statusCode(200)
      .header("Request-Host", hostHeader)
  }

  @Test
  def shouldReplaceHostHeaderByDefault(ctx: TestContext): Unit = {
    targetService.when(req().withPath("/should-replace-host-header-by-default")).respond { request: HttpRequest =>
      resp().withStatusCode(200).withHeader("Request-Host", request.getFirstHeader("Host"))
    }

    val hostHeader = "cloudentity.com"

    given()
      .header("Host", hostHeader)
    .when()
      .get(rulesTestBasePath + "/should-replace-host-header-by-default")
    .`then`()
      .statusCode(200)
      .header("Request-Host", s"localhost:7760")
  }

  @Test
  def shouldReturn504OnEventBusTimeout(ctx: TestContext): Unit = {
    given()
    .when()
      .get(rulesTestBasePath + "/should-return-504-on-event-bus-timeout")
    .`then`()
      .statusCode(504)
      .body(StringContains.containsString("System.Timeout"))
  }

  @Test
  def shouldSkipResponsePluginWhenApplyIfIsFalse(ctx: TestContext): Unit = {
    targetService.when(req().withPath("/should-skip-plugin-when-apply-if-false")).respond { request: HttpRequest =>
      resp().withStatusCode(200)
    }

    given()
    .when()
      .get(rulesTestBasePath + "/should-skip-plugin-when-apply-if-false")
    .`then`()
      .statusCode(200)
  }

  @Test
  def shouldApplyReroute(ctx: TestContext): Unit = {
    mockOnPath(targetService)("/rerouted", resp().withStatusCode(200))

    given()
    .when()
      .get(rulesTestBasePath + "/should-apply-reroute")
    .`then`()
      .statusCode(200)
  }

  @Test
  def shouldBreakRerouteLoop(ctx: TestContext): Unit = {
    given()
    .when()
      .get(rulesTestBasePath + "/should-break-reroute-loop")
    .`then`()
      .statusCode(500)
  }

  def resp(): HttpResponse = org.mockserver.model.HttpResponse.response()
  def req(): HttpRequest = org.mockserver.model.HttpRequest.request()
}

class TimeoutPluginVerticle extends RequestPluginVerticle[Unit] {
  override def name: PluginName = PluginName("timeout")

  override def apply(requestCtx: RequestCtx, conf: Unit): Future[RequestCtx] =
    Future.failed(new ReplyException(ReplyFailure.TIMEOUT, "Timed out"))

  override def validate(conf: Unit): ValidateResponse = ValidateResponse.ok()
  override def confDecoder: Decoder[Unit] = Decoder.decodeUnit
}

class FailPluginVerticle extends ResponsePluginVerticle[Unit] {
  override def name: PluginName = PluginName("fail")

  override def apply(responseCtx: ResponseCtx, conf: Unit): Future[ResponseCtx] =
    Future.failed(new RuntimeException)

  override def validate(conf: Unit): ValidateResponse = ValidateResponse.ok()
  override def confDecoder: Decoder[Unit] = Decoder.decodeUnit
}
