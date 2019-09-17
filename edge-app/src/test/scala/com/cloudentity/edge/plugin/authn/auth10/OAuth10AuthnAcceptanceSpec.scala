package com.cloudentity.edge.plugin.authn.auth10

import java.net.URI
import java.nio.charset.Charset
import java.security.PrivateKey

import com.mastercard.developer.oauth.OAuth
import com.cloudentity.edge.ApiGatewayTest
import com.cloudentity.edge.util.{OAuth10Data, OAuth10Utils, SecurityUtils}
import io.restassured.RestAssured.given
import io.restassured.filter.{Filter, FilterContext}
import io.restassured.specification.{FilterableRequestSpecification, FilterableResponseSpecification}
import org.hamcrest.core.Is
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response

class OAuth10AuthnAcceptanceSpec extends ApiGatewayTest with SecurityUtils with OAuth10Utils {

  override def getMainVerticle: String = "com.cloudentity.edge.Application"
  override def getMetaConfPath() = "src/test/resources/plugin/authn/oauth10/meta-config.json"

  var targetService: ClientAndServer = null
  val signingKey = loadPrivateKey("src/test/resources/plugin/authn/oauth10/sample-rsa.key")
  val consumerKey = "keyId"

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
    mockTargetService()
  }

  @After
  def finish(): Unit = {
    targetService.stop
  }

  def mockTargetService(): Unit = {
    targetService
      .when(request())
      .respond(response.withStatusCode(200).withBody("responseBody"))
  }

  @Test
  def shouldNotAuthenticateIfHeaderIsMissing(): Unit = {
    given()
      .when()
      .get("/sample-rs/test")
      .`then`()
      .statusCode(401)
  }

  @Test
  def shouldNotAuthenticateIfHeaderHasInvalidFormat(): Unit = {
    given()
      .header("Authorization", "123")
      .when()
      .get("/sample-rs/test")
      .`then`()
      .statusCode(401)
  }

  @Test
  def shouldNotAuthenticateWithNotSupportedSignature(): Unit = {
    val oauthHeader = buildAuthorizationHeader(OAuth10Data.sampleRequest.withMethod("invalid"))

    given()
      .header("Authorization", oauthHeader)
    .when()
      .get("/sample-rs/test")
    .`then`()
      .statusCode(400)
      .body("code", Is.is("Request.Invalid"))
      .body("message", Is.is("Unsupported signature method"))
  }

  @Test
  def callGet(): Unit = {
    given()
      .log().all()
      .filter(withSignedOAuthRequest(consumerKey, signingKey))
    .when()
      .get("/sample-rs/test")
    .`then`()
      .statusCode(200)
  }

  @Test
  def callGetWithQueryParams(): Unit = {
    given()
      .log().all()
      .filter(withSignedOAuthRequest(consumerKey, signingKey))
    .when()
      .get("/sample-rs/test?param1=value1&param2=value2")
    .`then`()
      .statusCode(200)
  }

  @Test
  def callPostWithBody(): Unit = {
    given()
      .log().all()
      .filter(withSignedOAuthRequest(consumerKey, signingKey))
      .body("{\"key\":\"value\"}")
    .when()
      .post("/sample-rs/test")
    .`then`()
      .statusCode(200)
  }

  def withSignedOAuthRequest(consumerKey: String, signingKey: PrivateKey): Filter = {
    (req: FilterableRequestSpecification, res: FilterableResponseSpecification, ctx: FilterContext) =>
      val header = OAuth.getAuthorizationHeader(URI.create(req.getURI), req.getMethod,
        req.getBody[String], Charset.defaultCharset(), consumerKey, signingKey)
      req.header("Authorization", header)
      ctx.next(req, res)
  }

}
