package com.cloudentity.pyron.plugin.impl.authn.methods

import java.util.Base64

import com.cloudentity.pyron.plugin.impl.PluginAcceptanceTest
import com.cloudentity.pyron.test.TestRequestResponseCtx
import io.restassured.RestAssured.given
import io.vertx.ext.unit.TestContext
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.scalatest.MustMatchers

class OAuthAuthorizationCodeIntrospectionTest extends PluginAcceptanceTest with MustMatchers with TestRequestResponseCtx {
  override def getMetaConfPath(): String = "src/test/resources/plugins/authn/oauth2-introspect/meta-config.json"

  var targetService: ClientAndServer = _
  var authorizationServer: ClientAndServer = _

  val authzHeader = "Basic " + Base64.getEncoder.encodeToString("client-id:client-secret".getBytes)

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
    authorizationServer = startClientAndServer(7761)
  }

  @After
  def finish(): Unit = {
    targetService.stop
    authorizationServer.stop
  }

  @Test
  def shouldAbstainIfTokenMissing(ctx: TestContext): Unit = {
    given()
    .when()
      .get("/oauth2-introspect")
    .`then`()
      .statusCode(401)
  }

  @Test
  def shouldReturnFailureIfTokenInactive(ctx: TestContext): Unit = {
    authorizationServer.when(request()).respond { request: HttpRequest =>
      if (request.getBodyAsString().startsWith("token=user-token")) {
        response().withStatusCode(200).withBody("""{"active": false}""")
      } else response().withStatusCode(404)
    }

    given()
      .header("Authorization", "Bearer user-token")
    .when()
      .get("/oauth2-introspect")
    .`then`()
      .statusCode(401)
  }

  @Test
  def shouldReturnFailureIfPyronUnauthorized(ctx: TestContext): Unit = {
    authorizationServer.when(request()).respond { request: HttpRequest =>
      if (request.getFirstHeader("Authorization").equals(authzHeader)) {
        response().withStatusCode(401)
      } else response().withStatusCode(200)
    }

    given()
      .header("Authorization", "Bearer user-token")
    .when()
      .get("/oauth2-introspect")
      .`then`()
      .statusCode(401)
  }

  @Test
  def shouldReturnSuccessIfTokenActive(ctx: TestContext): Unit = {
    targetService.when(request()).respond { request: HttpRequest =>
      response().withStatusCode(200)
    }

    authorizationServer.when(request()).respond { request: HttpRequest =>
      if (request.getBodyAsString().startsWith("token=user-token") && request.getFirstHeader("Authorization").equals(authzHeader)) {
        response().withStatusCode(200).withBody("""{"active": true}""")
      } else response().withStatusCode(404)
    }

    given()
      .header("Authorization", "Bearer user-token")
    .when()
      .get("/oauth2-introspect")
    .`then`()
      .statusCode(200)
  }

}
