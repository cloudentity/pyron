package com.cloudentity.pyron.plugin.impl.cors

import com.cloudentity.pyron.api.Responses.Errors
import com.cloudentity.pyron.domain.flow.{PluginName, RequestCtx}
import com.cloudentity.pyron.plugin.config.ValidateResponse
import com.cloudentity.pyron.plugin.impl.PluginAcceptanceTest
import com.cloudentity.pyron.plugin.verticle.RequestPluginVerticle
import io.circe.Decoder
import io.restassured.RestAssured.given
import io.restassured.http.Method
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.scalatest.MustMatchers

import scala.collection.JavaConverters._
import scala.concurrent.Future

class CorsPluginTest extends PluginAcceptanceTest with MustMatchers {
  var targetService: ClientAndServer = _

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
    targetService.when(request()).respond { request: HttpRequest =>
      response().withStatusCode(200)
    }
  }

  @After
  def finish(): Unit = {
    targetService.stop
  }

  override def getMetaConfPath(): String = "src/test/resources/plugins/cors/meta-config.json"

  @Test
  def shouldInjectAccessControlHeadersToResponse(): Unit = {
    call("http://example.com", Method.GET, "/cors", expectedWildcardHeaders, 200)
  }

  @Test
  def shouldInjectAccessControlHeadersEvenIfARequestPluginFails(): Unit = {
    call("http://example.com", Method.GET, "/cors-with-failing", expectedWildcardHeaders, 500)
  }

  @Test
  def shouldAllowToOverwriteCorsConfigurationPerEndpoint(): Unit = {
    call("http://example-b.com", Method.GET, "/cors-strict", expectedStrictHeaders, 200)
  }

  @Test
  def shouldAllowToMakeACallWithOptionsMethodAndReturnAccessControlHeaders(): Unit = {
    call("http://example.com", Method.OPTIONS, "/cors", expectedWildcardHeaders, 200)
  }

  @Test
  def shouldAllowToMakeACallWithOptionsAndUseOverwrittenConfigurationOfCors(): Unit = {
    call("http://example-b.com", Method.OPTIONS, "/cors-strict", expectedStrictHeaders, 200)
  }

  @Test
  def shouldReturnAllowedOriginsIfOriginDoesNotMatch(): Unit = {
    call("http://example-c.com", Method.OPTIONS, "/cors-strict", expectedNotMatchedOriginHeaders, 200)
  }

  val expectedWildcardHeaders = Map(
    "Access-Control-Allow-Credentials" -> "true",
    "Access-Control-Allow-Headers" -> "*",
    "Access-Control-Allow-Methods" -> "*",
    "Access-Control-Allow-Origin" -> "http://example.com",
    "Access-Control-Max-Age" -> "84000"
  )

  val expectedStrictHeaders = Map(
    "Access-Control-Allow-Headers" -> "Authentication",
    "Access-Control-Allow-Methods" -> "GET,POST",
    "Access-Control-Allow-Origin" -> "http://example-b.com",
    "Access-Control-Max-Age" -> "84000"
  )

  val expectedNotMatchedOriginHeaders = Map(
    "Access-Control-Allow-Headers" -> "Authentication",
    "Access-Control-Allow-Methods" -> "GET,POST",
    "Access-Control-Allow-Origin" -> "http://example-a.com,http://example-b.com",
    "Access-Control-Max-Age" -> "84000"
  )

  def call(origin: String, method: Method, path: String, expectedHeaders: Map[String, String], expectedStatus: Int) =
    given()
      .when()
      .header("Origin", origin)
      .request(method, path)
      .`then`()
      .statusCode(expectedStatus)
      .headers(expectedHeaders.asJava)
}

class FailingPlugin extends RequestPluginVerticle[Unit] {
  override def name: PluginName = PluginName("failing")
  override def apply(requestCtx: RequestCtx, conf: Unit): Future[RequestCtx] = Future.successful(requestCtx.abort(Errors.unexpected.toApiResponse))
  override def validate(conf: Unit): ValidateResponse = ValidateResponse.ok()
  override def confDecoder: Decoder[Unit] = Decoder.decodeUnit
}
