package com.cloudentity.pyron.plugin.impl.acp

import com.cloudentity.pyron.PyronAcceptanceTest
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import io.restassured.RestAssured.given
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import io.circe.syntax._
import org.mockserver.model.{Body, HttpRequest, JsonBody}

class AcpAuthzPluginTest extends PyronAcceptanceTest {
  override val getMetaConfPath = "src/test/resources/modules/plugin/acp-authz/meta-config.json"

  var targetService: ClientAndServer = null
  var authorizer: ClientAndServer = null

  @Before
  def before(): Unit = {
    targetService = ClientAndServer.startClientAndServer(7760)
    authorizer = ClientAndServer.startClientAndServer(7777)

    targetService
      .when(request().withMethod("GET").withPath("/user/abc"))
      .respond(response.withStatusCode(200))
  }

  @After
  def finish(): Unit = {
    targetService.stop
    authorizer.stop()
  }

  @Test
  def shouldSendApiGroupIdAndAPIsToAuthorizer(): Unit = {
    authorizer
      .when(request())
      .respond(response.withStatusCode(200))

    given
      .header("User-Agent", "user-agent")
    .when()
      .get("/a/1/user/abc?q=x")

    val expected =
      AuthorizeRequest(
        "a.1",
        "GET",
        "/user/abc",
        Map(
          "Accept" -> List("*/*"),
          "Accept-Encoding" -> List("gzip,deflate"),
          "Connection" -> List("Keep-Alive"),
          "Host" -> List("127.0.0.1:8080"),
          "User-Agent" -> List("user-agent")
        ),
        Map("q" -> List("x")),
        Map("userid" -> "abc")
      )

    authorizer.verify(new HttpRequest().withBody(JsonBody.json(expected.asJson.noSpaces)))
  }

  @Test
  def shouldAbortRequestWhenNon200FromAuthorizer(): Unit = {
    authorizer
      .when(request())
      .respond(response.withStatusCode(403))

    given
    .when()
      .get("/a/1/user/abc")
    .`then`()
      .statusCode(403)
  }

  @Test
  def shouldPassRequestWhen200FromAuthorizer(): Unit = {
    authorizer
      .when(request())
      .respond(response.withStatusCode(200))

    given
    .when()
      .get("/a/1/user/abc")
    .`then`()
      .statusCode(200)
  }
}
