package com.cloudentity.pyron.plugin.impl.transformer

import com.cloudentity.pyron.plugin.impl.PluginAcceptanceTest
import io.restassured.RestAssured.given
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.scalatest.MustMatchers

import scala.collection.JavaConverters._

class TransformRequestPluginDeploymentTest extends PluginAcceptanceTest with MustMatchers {
  override def getMetaConfPath: String = "src/test/resources/plugins/transformer/meta-config-deployment.json"

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
  def shouldDeployPluginWithoutPLUGIN_TRANSFORM_REQUEST_CONF_REF(): Unit = {
    given()
      .when()
      .get("/fixed-path-param/value")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req =>
      req.getPath mustBe "/fixed-path-param/fixed-param"
    }
  }

  def assertTargetRequest(f: HttpRequest => Unit): Unit = {
    targetService.retrieveRecordedRequests(null).length mustBe 1
    f(targetService.retrieveRecordedRequests(null)(0))
  }
}
