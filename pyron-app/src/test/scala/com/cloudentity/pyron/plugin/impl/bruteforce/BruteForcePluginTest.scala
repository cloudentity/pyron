package com.cloudentity.pyron.plugin.impl.bruteforce

import com.cloudentity.pyron.plugin.impl.PluginAcceptanceTest
import io.restassured.RestAssured.given
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.scalatest.MustMatchers

class BruteForcePluginTest extends PluginAcceptanceTest with MustMatchers  {
  var targetService: ClientAndServer = _

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)

    targetService.when(request()).callback { request: HttpRequest =>
      response().withStatusCode(request.getFirstHeader("TARGET_STATUS").toInt)
    }
  }

  @After
  def finish(): Unit = {
    targetService.stop
  }

  override def getMetaConfPath(): String = "src/test/resources/plugins/bruteforce/meta-config.json"

  @Test
  def shouldBlockAfterFirstWrongAttemptIf1MaxAttempt(): Unit = {
      callWithExpectedStatusAndTargetStatus("/brute-force-1-attempt", 401, 401)
      callWithExpectedStatusAndTargetStatus("/brute-force-1-attempt", 423, 401)
    }

  @Test
  def shouldUnlockWhenTargetServiceNotAvailable(): Unit = {
    targetService.stop()
    callWithExpectedStatusAndTargetStatus("/brute-force-3-attempt", 500, 401)
    callWithExpectedStatusAndTargetStatus("/brute-force-3-attempt", 500, 401)
  }

  @Test
  def shouldBlockAfterThirdWrongAttemptIf3MaxAttempt(): Unit = {
      callWithExpectedStatusAndTargetStatus("/brute-force-3-attempt", 401, 401)
      callWithExpectedStatusAndTargetStatus("/brute-force-3-attempt", 401, 401)
      callWithExpectedStatusAndTargetStatus("/brute-force-3-attempt", 401, 401)
      callWithExpectedStatusAndTargetStatus("/brute-force-3-attempt", 423, 200)
    }

  @Test
  def shouldResetFailedAttemptsIfSuccessfulResponse(): Unit = {
      callWithExpectedStatusAndTargetStatus("/brute-force-reset", 401, 401)
      callWithExpectedStatusAndTargetStatus("/brute-force-reset", 401, 401)
      callWithExpectedStatusAndTargetStatus("/brute-force-reset", 200, 200)
      callWithExpectedStatusAndTargetStatus("/brute-force-reset", 401, 401)
      callWithExpectedStatusAndTargetStatus("/brute-force-reset", 401, 401)
      callWithExpectedStatusAndTargetStatus("/brute-force-reset", 401, 401)
      callWithExpectedStatusAndTargetStatus("/brute-force-reset", 423, 200)
    }

  def callWithExpectedStatusAndTargetStatus(path: String, expectedStatus: Int, targetStatus: Int) = {
      given()
        .header("TARGET_STATUS", targetStatus)
        .header("ID", "id")
      .when()
        .get(path)
      .`then`()
        .statusCode(expectedStatus)
  }
}
