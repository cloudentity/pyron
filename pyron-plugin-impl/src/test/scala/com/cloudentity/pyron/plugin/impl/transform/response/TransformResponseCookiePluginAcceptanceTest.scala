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
    targetService.when(new HttpRequest()).respond(new HttpResponse().withStatusCode(200).withHeader("Set-Cookie", "foo=bar"))
  }

  @After
  def finish(): Unit = {
    targetService.stop
  }

  @Test
  def shouldChangeCookieValue(): Unit = {
    given()
    .when()
      .get("/transform-response-cookie")
    .`then`()
      .header("Set-Cookie", "foo=42")
      .statusCode(200)
  }
}
