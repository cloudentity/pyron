package com.cloudentity.edge.plugin.jwt

import com.cloudentity.edge.ApiGatewayTest
import com.cloudentity.edge.domain.flow.PluginName
import com.cloudentity.edge.jwt.{JwtService, JwtServiceFactory}
import com.cloudentity.edge.plugin.impl.jwt.OutgoingCustomJwtPlugin
import com.cloudentity.edge.util.{JwtUtils, MockUtils}
import io.restassured.RestAssured.given
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonObject
import org.hamcrest.core.IsEqual
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpResponse.response

class OutgoingCustomJwtPluginCustomHeader extends OutgoingCustomJwtPlugin {
  override def name = PluginName("outgoingCustomJwt-customHeader")
}

class OutgoingCustomJwtPluginSpec extends ApiGatewayTest with MockUtils with JwtUtils {
  override def getMetaConfPath() = "src/test/resources/outgoing-custom-jwt/meta-config.json"
  lazy val otherJwtService: JwtService = JwtServiceFactory.createClient(getVertx(), "other-symmetric")

  var targetService: ClientAndServer = null
  var sessionService: ClientAndServer = null

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
    sessionService = startClientAndServer(7750)

    mockOnPath(sessionService)("/hmac/session", response()
      .withBody("""{"user":{"id": "a"},"level":30}""")
      .withStatusCode(200)
    )
  }

  @After
  def after(): Unit = {
    targetService.stop
    sessionService.stop
  }

  @Test
  def shouldRemapJwtBasedOnDefinedMappingWithDefaultOutputHeader(): Unit = {
    mockOnPathWithPongingBodyAndHeaders(targetService)("/path-for-default-header", 201)

    given()
    .when()
      .header("token", "1234")
      .get("/service/path-for-default-header")
    .`then`()
      .statusCode(201)
      .header(
        HttpHeaders.AUTHORIZATION.toString,
        asJwtJson(otherJwtService).andThen(_.getJsonObject("cnt"))(_),
        IsEqual.equalTo(new JsonObject(
          """{"usr":{"id":"a"},"lvl":30,"status":"inactive"}"""
        ))
      )
  }

  @Test
  def shouldRemapJwtBasedOnDefinedMappingWithCustomOutputHeader(): Unit = {
    mockOnPathWithPongingBodyAndHeaders(targetService)("/path-for-custom-header", 201)

    given()
    .when()
      .header("token", "1234")
      .get("/service/path-for-custom-header")
    .`then`()
      .statusCode(201)
      .header(
        "x-cd-fingerprint",
        asJwtJson(otherJwtService, "(.*)").andThen(_.getJsonObject("cnt"))(_),
        IsEqual.equalTo(new JsonObject(
          """{"usr":{"id":"a"},"lvl":30,"status":"inactive"}"""
        ))
      )
  }
}
