package com.cloudentity.pyron.acceptance

import com.cloudentity.pyron.api.ApiRouter.{Path, RouteFilter}
import com.cloudentity.pyron.util.MockUtils
import com.cloudentity.pyron.{Application, PyronAcceptanceTest}
import io.restassured.RestAssured.`given`
import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import org.hamcrest.core.IsEqual
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpResponse.response
import org.scalatest.MustMatchers

class RouteFilterTestApplication extends Application {
  override def routeFilters(): List[(Path, Handler[RoutingContext])] =
    List[RouteFilter]("/" -> (ctx => ctx.reroute("/login")))
}

class RouteFilterTest extends PyronAcceptanceTest with MustMatchers with MockUtils {
  override def getMainVerticle: String = "com.cloudentity.pyron.acceptance.RouteFilterTestApplication"

  var targetService: ClientAndServer = _

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
  }

  @After
  def finish(): Unit = {
    targetService.stop
  }

  override def getMetaConfPath: String = "src/test/resources/acceptance/route-filter/meta-config.json"

  @Test
  def test(): Unit = {
    val targetCode = 200
    val targetBody = "targetBody"
    val resp =
      response()
        .withBody(targetBody)
        .withStatusCode(targetCode)

    mockOnPath(targetService)("/login", resp)

    given()
    .when()
      .get("/")
    .`then`()
      .statusCode(targetCode)
      .body(IsEqual.equalTo(targetBody))
  }
}
