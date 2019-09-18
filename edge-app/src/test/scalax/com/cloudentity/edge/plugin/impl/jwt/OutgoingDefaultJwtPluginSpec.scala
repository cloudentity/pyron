package com.cloudentity.edge.plugin.impl.jwt

import com.cloudentity.edge.ApiGatewayTest
import com.cloudentity.edge.domain.flow.PluginName
import com.cloudentity.edge.jwt.{JwtService, JwtServiceFactory}
import com.cloudentity.edge.plugin.impl.jwt.OutgoingDefaultJwtPlugin
import com.cloudentity.edge.util.{JwtUtils, MockUtils}
import io.restassured.RestAssured.given
import io.vertx.core.http.HttpHeaders
import org.hamcrest.core.IsEqual
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer

class OutgoingDefaultJwtPluginCustomHeader extends OutgoingDefaultJwtPlugin {
  override def name = PluginName("outgoingDefaultJwt-customHeader")
}

class OutgoingDefaultJwtPluginSpec extends ApiGatewayTest with MockUtils with JwtUtils {
  override def getMetaConfPath() = "src/test/resources/outgoing-default-jwt/meta-config.json"
  lazy val jwtService: JwtService = JwtServiceFactory.createClient(getVertx(), "symmetric")

  var targetService: ClientAndServer = null

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
  }

  @After
  def after(): Unit = {
    targetService.stop
  }

  @Test
  def shouldPutJwtInDefaultAuthorizationHeader(): Unit = {
    mockOnPathWithPongingBodyAndHeaders(targetService)("/path-for-default-header", 201)

    given()
    .when()
      .header("token", "1234")
      .get("/service/path-for-default-header")
    .`then`()
      .statusCode(201)
      .header(
        HttpHeaders.AUTHORIZATION.toString,
        asJwtJson(jwtService).andThen(_.getString("iss"))(_),
        IsEqual.equalTo("test-orchis-api-gateway")
      )
  }

  @Test
  def shouldPutJwtInCustomAuthorizationHeader(): Unit = {
    mockOnPathWithPongingBodyAndHeaders(targetService)("/path-for-custom-header", 201)

    given()
    .when()
      .header("token", "1234")
      .get("/service/path-for-custom-header")
    .`then`()
      .statusCode(201)
      .header(
        "x-cd-fingerprint",
        asJwtJson(jwtService, "(.*)").andThen(_.getString("iss"))(_),
        IsEqual.equalTo("test-orchis-api-gateway")
      )
  }
}
