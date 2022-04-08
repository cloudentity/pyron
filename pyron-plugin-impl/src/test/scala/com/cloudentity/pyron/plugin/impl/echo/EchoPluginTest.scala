package com.cloudentity.pyron.plugin.impl.echo

import com.cloudentity.pyron.plugin.impl.PluginAcceptanceTest
import io.restassured.RestAssured.`given`
import io.restassured.http.{Header, Headers, Method}
import org.json.JSONObject
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.scalatest.MustMatchers
import org.skyscreamer.jsonassert.JSONAssert

class EchoPluginTest extends PluginAcceptanceTest with MustMatchers {
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

  override def getMetaConfPath(): String = "src/test/resources/plugins/echo/meta-config.json"

  @Test
  def shouldInjectAccessControlHeadersToResponseWithBody(): Unit = {

    val inputHeaders = Headers.headers(new Header("test-header", "test-value"))
    val testBody = new JSONObject().put("some", "key")

    val jsonObject = new JSONObject(
      """ {"headers":{"test-header":["test-value"]},
        |"queryParams":"","uri":"/echotest4","method":"POST",
        | "body" : { "some" : "key"}}  """.stripMargin)

    callWithBody("http://example.com", Method.POST, "/echotest4", inputHeaders, testBody, jsonObject, 200)
  }

  @Test
  def shouldInjectAccessControlHeadersToResponseWithSelectedHeader(): Unit = {

    val inputHeaders = Headers.headers(new Header("test-header", "test-value"))
    val jsonObject = new JSONObject(
      """ {"headers":{"test-header":["test-value"]},
        |"queryParams":"",
        |"uri":"/echotest3",
        |"method":"GET",
        |"body" :{}}  """.stripMargin)
    call("http://example.com", Method.GET, "/echotest3", inputHeaders, jsonObject, 200)
  }

  val expectedWildcardHeaders = Map(
    "asd" -> "asdf"
  )

  def call(origin: String, method: Method, path: String, headers: Headers, jsonObject: JSONObject, expectedStatus: Int) = {

    val response  = given()
      .when()
      .headers(headers)
      .request(method, path)
      .`then`()
      .statusCode(expectedStatus)
      .contentType("application/json")
      .extract().response()

    JSONAssert.assertEquals("checking json", response.body().asString(), jsonObject, false)

  }

  def callWithBody(origin: String, method: Method, path: String, headers: Headers, body: JSONObject,  jsonObject: JSONObject, expectedStatus: Int) = {

    val response  = given()
      .body(body.toString)
      .when()
      .headers(headers)
      .request(method, path)
      .`then`()
      .statusCode(expectedStatus)
      .contentType("application/json")
      .extract().response()

    JSONAssert.assertEquals("checking json", response.body().asString(), jsonObject, false)

  }

}
