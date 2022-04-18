package com.cloudentity.pyron.acceptance

import com.cloudentity.pyron.PyronAcceptanceTest
import com.cloudentity.pyron.util.MockUtils
import io.restassured.RestAssured.`given`
import io.vertx.ext.unit.TestContext
import org.junit.{After, Before, Ignore, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.{HttpRequest, HttpResponse}
import scala.collection.JavaConverters._

class RerouteAcceptanceTest extends PyronAcceptanceTest with MockUtils {
  override def getMetaConfPath = "src/test/resources/acceptance/reroute/meta-config.json"

  var targetService: ClientAndServer = _

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
  }

  @After
  def finish(): Unit = {
    targetService.stop
  }

  @Ignore("Un-ignore when support for path-params in rewritePath added")
  @Test
  def shouldApplyRerouteWithPathParams(ctx: TestContext): Unit = {
    mockOnPath(targetService)("/rerouted/path-params/x", resp().withStatusCode(200))

    given()
    .when()
      .get("/should-apply-reroute/path-params/x")
    .`then`()
      .statusCode(200)
  }

  @Test
  def shouldApplyRerouteWithQuery(ctx: TestContext): Unit = {
    targetService.when(request().withPath("/rerouted/query")).callback { request: HttpRequest =>
      val hasQuery = request.getQueryStringParameters.asScala.find(p => p.getName == "abc" && p.getValues.asScala.contains("123")).isDefined
      if (request.getPath.getValue == "/rerouted/query" && hasQuery) resp().withStatusCode(200)
      else response().withStatusCode(404)
    }

    given()
    .when()
      .get("/should-apply-reroute/query?abc=123")
    .`then`()
      .statusCode(200)
  }

  @Test
  def shouldApplyRerouteWithMethodRewrite(ctx: TestContext): Unit = {
    mockOnPath(targetService)("/rerouted/method-rewrite", resp().withStatusCode(200))

    given()
    .when()
      .get("/should-apply-reroute/method-rewrite")
    .`then`()
      .statusCode(200)
  }

  def resp(): HttpResponse = org.mockserver.model.HttpResponse.response()
  def req(): HttpRequest = org.mockserver.model.HttpRequest.request()
}
