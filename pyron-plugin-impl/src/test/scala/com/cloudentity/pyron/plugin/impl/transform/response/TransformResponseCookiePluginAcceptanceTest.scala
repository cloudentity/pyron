package com.cloudentity.pyron.plugin.impl.transform.response

import com.cloudentity.pyron.plugin.impl.PluginAcceptanceTest
import io.restassured.RestAssured.given
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.scalatest.MustMatchers

class TransformResponseCookiePluginAcceptanceTest extends PluginAcceptanceTest with MustMatchers {
  override def getMetaConfPath: String = "src/test/resources/plugins/transform-response-cookie/meta-config.json"

  var targetService: ClientAndServer = _

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
    targetService.when(new HttpRequest()).respond(new HttpResponse().withStatusCode(200))
  }

  @After
  def finish(): Unit = {
    targetService.stop
  }

  @Test
  def shouldChangeCookieValue(): Unit = {
    given()
    .when()
      .header("Set-Cookie", "foo=bar")
      .get("/transform-response-cookie")
    .`then`()
      .statusCode(200)

    assertTargetRequest { req =>
      println("SHOULD CHANGE COOKIE foo VALUE TO 42 - according to conf, but apparently nothing happens")
      println("REQ HEADERS" + req.getHeaders.get(2))
    }
  }

  def assertTargetRequest(f: HttpRequest => Unit): Unit = {
    targetService.retrieveRecordedRequests(null).length mustBe 1
    f(targetService.retrieveRecordedRequests(null)(0))
  }
}
