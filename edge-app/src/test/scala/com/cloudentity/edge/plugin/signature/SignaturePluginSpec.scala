package com.cloudentity.edge.plugin.signature

import com.cloudentity.edge.ApiGatewayTest
import com.cloudentity.edge.util.MockUtils
import io.restassured.RestAssured.given
import org.hamcrest.core.StringStartsWith
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer

class SignaturePluginSpec extends ApiGatewayTest with MockUtils {
  override def getMetaConfPath() = "src/test/resources/signature/meta-config.json"

  var targetService: ClientAndServer = _

  @Test
  def shouldInjectSignature(): Unit = {
    mockOnPathWithPongingBodyAndHeaders(targetService)("/path", 201)

    given()
      .when()
        .get("/service/path")
      .`then`()
      .statusCode(201)
      .header("x-ce-fingerprint", StringStartsWith.startsWith("Bearer"))
      .header("x-ce-context", StringStartsWith.startsWith("Bearer"))
  }

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
  }

  @After
  def after(): Unit = {
    targetService.stop()
  }
}
