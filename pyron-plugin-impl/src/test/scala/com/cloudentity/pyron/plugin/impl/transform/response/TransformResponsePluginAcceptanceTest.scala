package com.cloudentity.pyron.plugin.impl.transform.response

import com.cloudentity.pyron.plugin.impl.PluginAcceptanceTest
import io.restassured.RestAssured.`given`
import org.junit.{After, Before, Test, Ignore}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.scalatest.MustMatchers

class TransformResponsePluginAcceptanceTest extends PluginAcceptanceTest with MustMatchers  {

  override def getMetaConfPath: String = "src/test/resources/plugins/transform-response/meta-config.json"

  var targetService: ClientAndServer = _

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
    targetService.when(new HttpRequest()).respond(new HttpResponse()
      .withStatusCode(200)
      .withHeader("H", "value")
    )
  }

  @After
  def finish(): Unit = {
    targetService.stop
  }

  @Test
  def shouldSetFixedHeader(): Unit = {
    given()
      .when()
      .get("/fixed-header")
      .`then`()
      .header("H", "new-value")
      .statusCode(200)
  }

}
