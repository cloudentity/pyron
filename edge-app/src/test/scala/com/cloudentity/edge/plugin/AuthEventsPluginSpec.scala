package com.cloudentity.edge.plugin

import com.cloudentity.edge.ApiGatewayTest
import com.cloudentity.edge.util.{JwtUtils, MockUtils}
import io.restassured.RestAssured.given
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpResponse.response


class AuthEventsPluginSpec  extends ApiGatewayTest with MockUtils with JwtUtils {

  override def getMetaConfPath() = "src/test/resources/authevents/meta-config.json"

  var authnEventsService: ClientAndServer = _
  var targetService: ClientAndServer = _
  var sessionService: ClientAndServer = _

  val ssoToken = "ssoToken"
  val ssoTokenHeaderName = "token"
  val ssoTokenType = "sso"

  val accessToken = "abc"
  val accessTokenHeaderName = "Authorization"
  val accessTokenType = "accessTokenOAuth2"

  val addSsoEventBody = "{\"sessionId\":{\"sessionKey\":\"" + ssoToken + "\",\"sessionType\":\"" + ssoTokenType + "\"},\"authEvent\":{\"type\":\"AuthN\",\"id\":\"FederatedAuthentication\",\"success\":true}}"
  val addAccessTokenEventBody = "{\"sessionId\":{\"sessionKey\":\"" + accessToken + "\",\"sessionType\":\"" + accessTokenType + "\"},\"authEvent\":{\"type\":\"AuthN\",\"id\":\"FederatedAuthentication\",\"success\":true}}"

  val copySsoEventBody = "{\"from\":{\"sessionKey\":\"ssoToken\",\"sessionType\":\"sso\"},\"to\":{\"sessionKey\":\"Q.ey9.E-qo\",\"sessionType\":\"accessTokenOAuth2\"}}"

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
    authnEventsService = startClientAndServer(7740)
    sessionService = startClientAndServer(7770)
  }

  @After
  def after(): Unit = {
    stopMock(authnEventsService)
    stopMock(targetService)
    stopMock(sessionService)
  }

  def stopMock(mock: ClientAndServer) = {
    while(mock.isRunning) {
      mock.stop(true)
      if (mock.isRunning) sleepAWhile()
    }
  }

  @Test
  def shouldSendAuthEventForSessionTokenFromAuthnCtx() = {
    mockOnPath(targetService)("/send-event-to-session-from-context", response().withStatusCode(200))
    mockOnPathWithBody(authnEventsService)("/authevents/add", addSsoEventBody, response().withStatusCode(201))
    mockOnPath(sessionService)("/hmac/session", response()
      .withBody("""{"uuid":"j", "name": "janusz"}""")
      .withStatusCode(200))

    given()
      .header(ssoTokenHeaderName, ssoToken)
    .when()
      .post("/service/send-event-to-session-from-context")
    .`then`()
      .statusCode(200)
    sleepAWhile() //give time for plugin to actually send request to authz

  }

  @Test
  def shouldFailWith500WhenSessionTokenWasNotFoundInAuthnCtx() = {
    mockOnPath(targetService)("/send-event-without-authn-plugin", response().withStatusCode(200))

    given()
      .when()
      .post("/service/send-event-without-authn-plugin")
      .`then`()
      .statusCode(500)
    sleepAWhile() //give time for plugin to actually send request to authz

    verifyRequestNotInvoked(authnEventsService)("/authevents/add", addSsoEventBody)
  }

  @Test
  def shouldSkipSendingAuthEventWhenSessionNotFound() = {
    mockOnPath(targetService)("/send-event-to-session-from-context", response().withStatusCode(200))
    mockOnPath(sessionService)("/hmac/session", response()
      .withStatusCode(404))

    given()
      .header(ssoTokenHeaderName, ssoToken)
      .when()
      .post("/service/send-event-to-session-from-context")
      .`then`()
      .statusCode(401)
    sleepAWhile() //give time for plugin to actually send request to authz

    verifyRequestNotInvoked(authnEventsService)("/authevents/add", addSsoEventBody)
  }
}