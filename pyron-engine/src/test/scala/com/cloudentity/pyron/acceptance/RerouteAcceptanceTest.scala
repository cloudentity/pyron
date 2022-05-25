package com.cloudentity.pyron.acceptance

import com.cloudentity.pyron.PyronAcceptanceTest
import com.cloudentity.pyron.domain.flow.{PluginName, RequestCtx, ResponseCtx}
import com.cloudentity.pyron.plugin.config.ValidateResponse
import com.cloudentity.pyron.plugin.verticle.{RequestPluginVerticle, ResponsePluginVerticle}
import com.cloudentity.pyron.util.MockUtils
import io.circe.Decoder
import io.restassured.RestAssured.`given`
import io.vertx.ext.unit.TestContext
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.scalatest.MustMatchers

import scala.collection.JavaConverters._
import scala.concurrent.Future

class RerouteAcceptanceTest extends PyronAcceptanceTest with MockUtils with MustMatchers {
  override def getMetaConfPath = "src/test/resources/acceptance/reroute/meta-config.json"

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
  def shouldApplyRerouteWithPathParams(ctx: TestContext): Unit = {
    mockOnPath(targetService)("/rerouted/path-params/x", resp().withStatusCode(200))

    given()
    .when()
      .get("/should-apply-reroute/path-params/x")
    .`then`()
      .statusCode(200)
  }

  @Test
  def shouldApplyRerouteWithQuery(ctx: TestContext): Unit = {
    targetService.when(request().withPath("/rerouted/query")).respond { request: HttpRequest =>
      val hasQuery = request.getQueryStringParameters.getValues("abc").asScala.contains("123")
      if (request.getPath.getValue == "/rerouted/query" && hasQuery) resp().withStatusCode(200)
      else response().withStatusCode(404)
    }

    given()
    .when()
      .get("/should-apply-reroute/query?abc=123")
    .`then`()
      .statusCode(200)
  }

  @Test
  def shouldApplyRerouteWithMethodRewrite(ctx: TestContext): Unit = {
    targetService.when(request().withPath("/rerouted/method-rewrite").withMethod("POST")).respond(resp().withStatusCode(200))

    given()
    .when()
      .get("/should-apply-reroute/method-rewrite")
    .`then`()
      .statusCode(200)
  }

  import scala.collection.JavaConverters._
  @Test
  def shouldApplyPluginsFromInitialAndReroutedRule(ctx: TestContext): Unit = {
    mockOnPathWithPongingBodyAndHeaders(targetService)("/rerouted/plugins", 200)

    val headerValuesAppendedByPlugins = given()
    .when()
      .get("/should-apply-reroute/plugins").headers().getValues("X-PLUGIN").asScala.toList

    headerValuesAppendedByPlugins mustBe List("request-initial", "request-rerouted", "response-rerouted", "response-initial")
  }

  def resp(): HttpResponse = org.mockserver.model.HttpResponse.response()
  def req(): HttpRequest = org.mockserver.model.HttpRequest.request()
}

case class ReroutePluginConf(value: String)
import io.circe.generic.semiauto._
class RerouteRequestTestPlugin extends RequestPluginVerticle[ReroutePluginConf] {
  override def apply(request: RequestCtx, conf: ReroutePluginConf): Future[RequestCtx] = Future.successful {
    request.modifyRequest(_.modifyHeaders(_.add("X-PLUGIN", conf.value)))
  }

  override def name: PluginName = PluginName("reroute-request")

  override def validate(conf: ReroutePluginConf): ValidateResponse = ValidateResponse.ok()

  override def confDecoder: Decoder[ReroutePluginConf] = deriveDecoder[ReroutePluginConf]
}

class RerouteResponseTestPlugin extends ResponsePluginVerticle[ReroutePluginConf] {
  override def apply(request: ResponseCtx, conf: ReroutePluginConf): Future[ResponseCtx] = Future.successful {
    request.modifyResponse(_.modifyHeaders(_.add("X-PLUGIN", conf.value)))
  }

  override def name: PluginName = PluginName("reroute-response")

  override def validate(conf: ReroutePluginConf): ValidateResponse = ValidateResponse.ok()

  override def confDecoder: Decoder[ReroutePluginConf] = deriveDecoder[ReroutePluginConf]
}
