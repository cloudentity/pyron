package com.cloudentity.edge.plugin.correlation

import io.circe.parser._
import com.cloudentity.edge.domain.Codecs._
import com.cloudentity.edge.ApiGatewayTest
import com.cloudentity.edge.domain.flow.{CorrelationCtx, RequestCtx}
import com.cloudentity.edge.domain.http.Headers
import com.cloudentity.edge.test.TestRequestResponseCtx
import io.restassured.RestAssured.given
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.scalatest.MustMatchers

class CorrelationIdPluginSpec extends ApiGatewayTest with MustMatchers with TestRequestResponseCtx {
  var targetService: ClientAndServer = _

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
  }

  @After
  def finish(): Unit = {
    targetService.stop
  }

  override def getMetaConfPath(): String = "src/test/resources/correlation/meta-config.json"

  @Test
  def shouldNotCreateMissingCorrelationIdIfNoopConfigured(): Unit = {
      val response =
        given().when().get("/noop").getBody.print()
      val headers = readHeaders(response)

      headers.get("CORRELATION_ID") must be(None)
    }

  @Test
  def shouldSetCorrelationIdInCorrelationCtx(): Unit = {
      val response =
        given()
          .header("CORRELATION_ID", "123")
        .when().get("/noop").getBody.print()
      val ctx = readCorrelationCtx(response)

      ctx.signature must endWith(" 123")
    }

  @Test
  def shouldRewriteHeaderWithoutGeneration(): Unit = {
      val response =
        given()
          .header("CORRELATION_ID", "123")
        .when().get("/rewrite").getBody.print()
      val headers = readHeaders(response)

      headers.get("CORRELATION_ID") must be(None)
      headers.getValues("REWRITTEN_CORRELATION_ID") must be(Some(List("123")))
    }

  @Test
  def shouldFillHeaderIfMissingInFillMode(): Unit = {
      val response =
        given().when().get("/fill").getBody.print()
      val headers = readHeaders(response)

      headers.get("CORRELATION_ID").isDefined must be(true)
    }

  @Test
  def shouldNotOverwriteHeaderIfPresentInFillMode(): Unit = {
      val response =
        given()
          .header("CORRELATION_ID", "123")
        .when().get("/fill").getBody.print()
      val headers = readHeaders(response)

      headers.getValues("CORRELATION_ID") must be(Some(List("123")))
    }

  @Test
  def shouldFillHEadeerIfMissingInOverwriteMode(): Unit = {
      val response =
        given().when().get("/overwrite").getBody.print()
      val headers = readHeaders(response)

      headers.get("CORRELATION_ID").isDefined must be(true)
    }

  @Test
  def shouldOverwriteHeaderIfPresentInOverwriteMode(): Unit = {
      val response =
        given()
          .header("CORRELATION_ID", "123")
        .when().get("/overwrite").getBody.print()
      val headers = readHeaders(response)

      headers.get("CORRELATION_ID").get must not be("123")
    }

  def readHeaders(response: String): Headers =
    decode[RequestCtx](response).right.get.request.headers

  def readCorrelationCtx(response: String): CorrelationCtx =
    decode[RequestCtx](response).right.get.correlationCtx
}
