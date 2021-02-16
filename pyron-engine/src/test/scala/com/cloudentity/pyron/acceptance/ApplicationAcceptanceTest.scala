package com.cloudentity.pyron.acceptance

import com.cloudentity.pyron.PyronAcceptanceTest
import com.cloudentity.pyron.util.MockUtils
import io.restassured.RestAssured.given
import org.hamcrest.core.IsEqual
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpResponse.response
import org.scalatest.MustMatchers

class ApplicationAcceptanceTest extends PyronAcceptanceTest with MustMatchers with MockUtils {
  override def getMetaConfPath = "src/test/resources/acceptance/application/meta-config-min.json"

  var targetService: ClientAndServer = _

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
  }

  @After
  def finish(): Unit = {
    targetService.stop
  }

  @Test
  def shouldReturnResponseFromTargetServiceWhenRequestPathMatchersRuleWithoutPolicy(): Unit = {
      val targetCode = 200
      val targetBody = "targetBody"
      val resp =
        response()
          .withBody(targetBody)
          .withStatusCode(targetCode)

      mockOnPath(targetService)("/path-without-policy", resp)

      given()
      .when()
        .post("/service/path-without-policy")
        .`then`()
        .statusCode(targetCode)
        .body(IsEqual.equalTo(targetBody))
  }

  @Test
  def shouldReturn404WhenPathMatchesRuleWithoutPolicy(): Unit = {
      given()
        .header("token", "sometoken")
      .when()
        .post("/whatever")
      .`then`()
        .statusCode(404)
  }
}
