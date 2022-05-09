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

    targetService.when(request()).respond { request: HttpRequest =>
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
  def shouldBlockAfterFirstWrongAttemptIfMaxAttemptsForCaseInSensitiveIdentifier(): Unit = {
    callWithExpectedStatusAndTargetStatus("/brute-force-4-attempt-case-insensitive", 401, 401)
    callWithExpectedStatusAndTargetStatus("/brute-force-4-attempt-case-insensitive", 401, 401, "ID")
    callWithExpectedStatusAndTargetStatus("/brute-force-4-attempt-case-insensitive", 423, 401, "id")
    callWithExpectedStatusAndTargetStatus("/brute-force-4-attempt-case-insensitive", 423, 401, "ID")
  }

  @Test
  def shouldBlockAfterFirstWrongAttemptIfMaxAttemptsForCaseInSensitiveIdentifierExplicitConfig(): Unit = {
    callWithExpectedStatusAndTargetStatus("/brute-force-4-attempt-case-insensitive-explicit-override", 401, 401)
    callWithExpectedStatusAndTargetStatus("/brute-force-4-attempt-case-insensitive-explicit-override", 401, 401, "ID")
    callWithExpectedStatusAndTargetStatus("/brute-force-4-attempt-case-insensitive-explicit-override", 423, 401, "id")
    callWithExpectedStatusAndTargetStatus("/brute-force-4-attempt-case-insensitive-explicit-override", 423, 401, "ID")
  }

  @Test
  def shouldBlockAfterDefinedWrongAttemptIfMaxAttemptsForCaseSensitiveIdentifier(): Unit = {
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive", 401, 401, "id")
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive", 401, 401, "ID")
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive", 401, 401, "id")

    //should lock only after explicit failures on "id"
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive", 423, 401, "id")

    //"ID" should not be locked
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive", 401, 401, "ID")

    //Now "ID should be locked"
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive", 423, 401, "ID")
  }

  @Test
  def shouldResetOnlyForCaseSensitiveIdentifierMatch(): Unit = {
    // make 2 lock attempts
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive-reset", 401, 401, "id")
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive-reset", 401, 401, "id")
    //reset lock
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive-reset", 200, 200, "id")
    // make 3 more lock attempts
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive-reset", 401, 401, "id")
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive-reset", 401, 401, "id")
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive-reset", 401, 401, "id")

    // ensure id is locked now and not reset
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive-reset", 423, 200, "id")


    // make 3 more lock attempts with different cases
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive-reset", 401, 401, "another-ID")
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive-reset", 401, 401, "another-ID")
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive-reset", 401, 401, "another-Id")

    //reset lock for "another-Id"
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive-reset", 200, 200, "another-Id")

    // make 3 more lock attempts with different cases
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive-reset", 401, 401, "another-ID")
    // ensure id is locked now and not reset
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive-reset", 423, 200, "another-ID")

    // Check if "another-Id" has 3 more attempts after reset
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive-reset", 401, 401, "another-Id")
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive-reset", 401, 401, "another-Id")
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive-reset", 401, 401, "another-Id")

    // ensure "another-Id" is locked now
    callWithExpectedStatusAndTargetStatus("/brute-force-5-attempt-case-sensitive-reset", 423, 401, "another-Id")

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

  def callWithExpectedStatusAndTargetStatus(path: String, expectedStatus: Int, targetStatus: Int, identifierValue: String = "id") = {
      given()
        .header("TARGET_STATUS", targetStatus)
        .header("ID", identifierValue)
      .when()
        .get(path)
      .`then`()
        .statusCode(expectedStatus)
  }
}
