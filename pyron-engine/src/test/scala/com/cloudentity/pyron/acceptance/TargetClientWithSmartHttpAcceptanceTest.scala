package com.cloudentity.pyron.acceptance

import com.cloudentity.pyron.PyronAcceptanceTest
import io.restassured.RestAssured.given
import org.hamcrest.core.{IsEqual, StringContains}
import org.junit.{After, Assert, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.{Delay, Header}
import org.scalatest.MustMatchers
import io.restassured.http.{Header => RestHeader}

import scala.collection.JavaConverters._

class TargetClientWithSmartHttpAcceptanceTest extends PyronAcceptanceTest with MustMatchers {
  var targetService: ClientAndServer = _

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
  }

  @After
  def finish(): Unit = {
    targetService.stop
  }

  override def getMetaConfPath: String = "src/test/resources/acceptance/http/meta-config.json"

  @Test
  def targetClientUsingSmartHttpClientShouldProxyHeadersAndBodyAndSetInternalJwtHeader(): Unit = {

    val requestHeaders = Map("x" -> "y", "z" -> "w")
    val requestBody = "body"

    val targetResponseBody = "responseBody"
    val targetResponseHeaderKey = "header-key"
    val targetResponseHeaderValue = "header-value"

    targetService
      .when(request())
      .respond(
        response
          .withStatusCode(200)
          .withBody(targetResponseBody)
          .withHeader(targetResponseHeaderKey, targetResponseHeaderValue)
      )

    given()
      .headers(requestHeaders.asJava)
      .body(requestBody)
    .when()
      .post("/discoverable-service/test")
    .`then`()
      .statusCode(200)
      .header(targetResponseHeaderKey, targetResponseHeaderValue)
      .body(IsEqual.equalTo(targetResponseBody))

    val requests = targetService.retrieveRecordedRequests(null)
    requests.size mustBe 1

    targetService.verify(
      request()
        .withBody(requestBody)
        .withHeaders(toMockHeaders(requestHeaders).asJava)
    )
  }

  @Test
  def targetClientUsingSmartHttpClientWithServiceTagsShouldCallTargetService(): Unit = {
    val requestBody = "body"

    targetService
      .when(request())
      .respond(
        response
          .withStatusCode(200)
          .withBody(requestBody)
      )

    given()
      .body(requestBody)
    .when()
      .post("/discoverable-service-with-tags/test")
    .`then`()
      .statusCode(200)
      .body(IsEqual.equalTo(requestBody))

    val requests = targetService.retrieveRecordedRequests(null)
    requests.size mustBe 1

    targetService.verify(
      request()
        .withBody(requestBody)
    )
  }

  @Test
  def targetClientUsingSmartHttpClientWithDefaultConfigShouldCallTargetService(): Unit = {
    val requestBody = "body"

    targetService
      .when(request())
      .respond(
        response
          .withStatusCode(400)
          .withBody(requestBody)
      )

    given()
      .body(requestBody)
    .when()
      .post("/service-default-config/test")
    .`then`()
      .statusCode(400)
      .body(IsEqual.equalTo(requestBody))

    val requests = targetService.retrieveRecordedRequests(null)
    requests.size mustBe 2

    targetService.verify(
      request()
        .withBody(requestBody)
    )
  }

  @Test
  def targetClientForStaticServiceShouldReturn504OnResponseTimeout(): Unit = {
    // given
    val requestBody = "body"

    targetService
      .when(request())
      .respond(
        response
          .withDelay(Delay.milliseconds(200)).applyDelay()
          .withStatusCode(200)
          .withBody(requestBody)
      )

    given()
      .body(requestBody)
    .when()
      .post("/static-service/test")
    .`then`()
      .statusCode(504)
      .body(StringContains.containsString("Response.Timeout"))

    val requests = targetService.retrieveRecordedRequests(null)
    requests.size mustBe 1
  }

  @Test
  def targetClientUsingSmartHttpClientShouldProxyAllHeaderValues(): Unit = {
    targetService
      .when(request().withHeader("a", "1", "2", "3"))
      .respond(response.withStatusCode(200).withHeader("b", "4", "5"))

    val httpRequest = io.restassured.RestAssured.given.header("a", "1", "2", "3")
    val httpResponse = httpRequest.post("/discoverable-service/test")

    Assert.assertEquals(List(
      new RestHeader("b", "4"),
      new RestHeader("b", "5")).asJava,
      httpResponse.headers().getList("b")
    )
    Assert.assertEquals(200, httpResponse.statusCode())
  }

  def toMockHeaders(hs: Map[String, String]): List[Header] =
    hs.map(x => Header.header(x._1, x._2)).toList
}
