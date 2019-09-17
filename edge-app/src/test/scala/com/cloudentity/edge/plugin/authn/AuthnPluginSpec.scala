package com.cloudentity.edge.plugin.authn

import com.cloudentity.edge.ApiGatewayTest
import com.cloudentity.edge.jwt.{JwtService, JwtServiceFactory}
import com.cloudentity.edge.util.{JwtUtils, MockUtils, SecurityUtils}
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpResponse.response
import io.restassured.RestAssured.given
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonObject
import org.hamcrest.core.IsEqual

class AuthnPluginSpec extends ApiGatewayTest with MockUtils with JwtUtils with SecurityUtils {
  lazy val jwtService: JwtService = JwtServiceFactory.createClient(getVertx, "symmetric")

  var targetService: ClientAndServer = null
  var sessionService: ClientAndServer = null
  var oidcService: ClientAndServer = null


  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
    sessionService = startClientAndServer(7750)
    oidcService = startClientAndServer(9987)
  }

  @After
  def after(): Unit = {
    targetService.stop
    sessionService.stop
    oidcService.stop
  }

  @Test
  def shouldFtcheUserAndDeviceEntityForValidToken(): Unit = {
    mockOnPath(sessionService)("/hmac/session", response()
      .withBody("""{"uuid":"a", "deviceUuid": "b"}""")
      .withStatusCode(200)
    )

    mockOnPath(sessionService)("/hmac/users/a", response()
      .withBody("""{"uuid":"a", "name": "andrzej"}""")
      .withStatusCode(200)
    )

    mockOnPath(sessionService)("/devices/b", response()
      .withBody("""{"uuid":"b", "name": "android"}""")
      .withStatusCode(200)
    )

    mockOnPathWithPongingBodyAndHeaders(targetService)("/path-for-authn-plugin", 201)

    given()
    .when()
      .header("token", "1234")
      .get("/service/path-for-authn-plugin")
    .`then`()
      .statusCode(201)
      .header(
      HttpHeaders.AUTHORIZATION.toString,
      asJwtJson(jwtService).andThen(_.getJsonObject("content"))(_),
      IsEqual.equalTo(new JsonObject("""
          {
            "token": "1234",
            "tokenType": "sso",
            "session": {"uuid": "a", "deviceUuid": "b"},
            "authnMethod": "sso",
            "user": {"uuid": "a", "name": "andrzej"},
            "userUuid": "a",
            "device": {"uuid": "b", "name": "android"},
            "deviceUuid": "b"
          }
          """))
      )
  }

  @Test
  def shouldRemoveCookieForInvalidCookie(): Unit = {
    mockOnPath(sessionService)("/hmac/session", response().withStatusCode(401))

    given()
      .cookie("token", "invalid")
    .when()
      .get("/service/path-for-authn-plugin")
    .`then`()
      .statusCode(401)
      .assertThat().cookie("token", "")
  }

  @Test
  def shouldReadAuthnContextFromProvidedJwt(): Unit = {
    mockOnPathWithPongingBodyAndHeaders(targetService)("/authn-with-jwt", 201)

    given()
    .when()
      .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImNvbnRlbnQiOnsidXNlciI6eyJuYW1lIjoidGVzdCJ9fX0.M0QxQW-ji4Kx1vbIjyyR6xD0Rco1wZ0QQ79AD6xT6e0")
      .get("/service/authn-with-jwt")
    .`then`()
      .statusCode(201)
      .header(
        HttpHeaders.AUTHORIZATION.toString,
        asJwtJson(jwtService).andThen(_.getJsonObject("content"))(_),
        IsEqual.equalTo(new JsonObject("""
          {
            "user": {"name": "test"},
            "tokenType":"jwt",
            "authnMethod": "jwt"
          }
          """))
      )
  }

  @Test
  def shouldFetchApplicationEntityForClientCredentialsOauthToken(): Unit = {
    val kid = "rsa1"
    val keyPair = generateRsaKeyPair
    val jwkSet = toJwkSet(generateRsaJwk(keyPair, kid))

    val uuid = "1234"
    val payload = new JsonObject().put("sub", uuid)
    val accessToken = generateRsaSignedJwt(keyPair, kid, payload)

    val jwkResponse = response().withStatusCode(200).withBody(jwkSet.toString)
    mockOnPath(oidcService)("/oauth/jwk", jwkResponse)

    mockOnPath(sessionService)("/application/capability/oauthClient/1234", response()
      .withBody("""{"id":"a", "customer": "b"}""")
      .withStatusCode(200)
    )
    mockOnPathWithPongingBodyAndHeaders(targetService)("/client-credentials-flow", 201)

    given()
    .when()
      .header("Authorization", "Bearer " + accessToken)
      .get("/service/client-credentials-flow")
    .`then`()
      .statusCode(201)
      .header(
        HttpHeaders.AUTHORIZATION.toString,
        asJwtJson(jwtService).andThen(_.getJsonObject("content"))(_),
        IsEqual.equalTo(new JsonObject("""
          {
            "authnMethod": "clientCredentialsOAuth",
            "tokenType": "accessTokenOAuth2",
            "application": {"id": "a", "customer": "b"},
            "oAuthClientId":"1234"
          }
          """))
      )
  }
}
