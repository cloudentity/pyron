package com.cloudentity.pyron.acceptance

import com.cloudentity.pyron.PyronAcceptanceTest
import com.cloudentity.pyron.util.MockUtils
import io.restassured.RestAssured.given
import io.vertx.core.json.{JsonArray, JsonObject}
import org.junit.Test

class RulesReloadAcceptanceTest extends PyronAcceptanceTest with MockUtils {
  override def getMetaConfPath: String = "src/test/resources/acceptance/rules-reload/meta-config.json"

  @Test
  def shouldReloadRulesArray(): Unit = {
      given()
        .when().get("/path")
        .`then`().statusCode(200)

      vertx.eventBus().publish(
        "reload-events-address",
        new JsonObject().put("rules", new JsonArray())
      )

      Thread.sleep(500)

      given()
        .when().get("/path")
        .`then`().statusCode(404)
    }

  @Test
  def shouldReloadRulesMap(): Unit = {
    given()
      .when().get("/path")
      .`then`().statusCode(200)

    vertx.eventBus().publish(
      "reload-events-address",
      new JsonObject().put("rules", new JsonObject().put("service-a", new JsonArray()))
    )

    Thread.sleep(500)

    given()
      .when().get("/path")
      .`then`().statusCode(404)
  }
}
