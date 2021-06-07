package com.cloudentity.pyron.plugin.impl.transform.response

import com.cloudentity.pyron.plugin.impl.PluginAcceptanceTest
import io.restassured.RestAssured.`given`
import org.hamcrest.core.IsEqual.equalTo
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.scalatest.MustMatchers

import scala.collection.JavaConverters._

class TransformResponsePluginAcceptanceTest extends PluginAcceptanceTest with MustMatchers {

  var targetService: ClientAndServer = _

  override def getMetaConfPath: String = "src/test/resources/plugins/transform-response/meta-config.json"

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
    targetService.when(new HttpRequest()).respond(new HttpResponse()
      .withHeader("Content-Type", "application/json")
      .withBody("""{"h": "bodyParamValue"}""")
      .withHeader("userUuid", "12345")
      .withStatusCode(200)
    )
  }

  @After
  def finish(): Unit = {
    targetService.stop
  }

  @Test
  def shouldSetBodyAttributeFromPathParam(): Unit = {
    given()
      .when()
      .get("/body-from-path-param/pathParamValue")
      .`then`()
      .body("attr", equalTo("pathParamValue"))
      .statusCode(200)
  }

  @Test
  def shouldSetBodyAttributeFromQueryParam(): Unit = {
    given()
      .queryParam("param", "one", "two", "six")
      .when()
      .get("/body-from-query-param")
      .`then`()
      .body("attr", equalTo(new java.util.ArrayList(List("one", "two", "six").asJava)))
      .statusCode(200)
  }

  @Test
  def shouldSetBodyAttributeFromCookie(): Unit = {
    given()
      .cookie("param", "cookieParamValue")
      .when()
      .get("/body-from-cookie")
      .`then`()
      .body("attr", equalTo("cookieParamValue"))
      .statusCode(200)
  }

    @Test
    def shouldSetBodyAttributeFromConf(): Unit = {
      given()
        .when()
        .get("/body-from-conf")
        .`then`()
        .body("attr", equalTo("confParamValue"))
        .statusCode(200)
    }


  @Test
  def shouldSetFixedHeader(): Unit = {
    given()
      .when()
      .get("/fixed-header")
      .`then`()
      .header("H", "value")
      .statusCode(200)
  }

  @Test
  def shouldSetHeaderFromDifferentResponseHeader(): Unit = {
    given()
      .when()
      .get("/header-from-different-response-header")
      .`then`()
      .header("H", "12345")
      .statusCode(200)
  }

  @Test
  def shouldSetHeaderFromPathParam(): Unit = {
    given()
      .when()
      .get("/header-from-path-param/pathParamValue")
      .`then`()
      .header("H", "pathParamValue")
      .statusCode(200)
  }

  @Test
  def shouldSetHeaderFromQueryParam(): Unit = {
    given()
      .when()
      .queryParam("param", "queryParamValue")
      .get("/header-from-query-param")
      .`then`()
      .header("H", "queryParamValue")
      .statusCode(200)
  }

  @Test
  def shouldSetHeaderFromCookie(): Unit = {
    given()
      .when()
      .cookie("param", "cookieParamValue")
      .get("/header-from-cookie")
      .`then`()
      .header("H", "cookieParamValue")
      .statusCode(200)
  }

  @Test
  def shouldSetHeaderFromResponseBody(): Unit = {
    given()
      .when()
      .get("/header-from-response-body")
      .`then`()
      .header("H", "bodyParamValue")
      .statusCode(200)
  }

  @Test
  def shouldSetHeaderFromConf(): Unit = {
    given()
      .when()
      .get("/header-from-conf")
      .`then`()
      .header("H", "confParamValue")
      .statusCode(200)
  }

}
