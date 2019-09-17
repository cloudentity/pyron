package com.cloudentity.edge.rule

import com.cloudentity.edge.ApiGatewayTest
import com.cloudentity.edge.util.MockUtils
import io.restassured.RestAssured.given
import io.vertx.core.json.{JsonArray, JsonObject}
import org.junit.Test

class RulesReloadSpec extends ApiGatewayTest with MockUtils {
  override def getMetaConfPath(): String = "src/test/resources/rules-reload/meta-config.json"

  @Test
  def shouldReloadRulesArray(): Unit = {
      given()
        .when().get("/path")
        .`then`().statusCode(200)

      vertx.eventBus().publish("reload-events-address", new JsonObject().put("rules", new JsonArray()))

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

    vertx.eventBus().publish("reload-events-address", new JsonObject().put("rules", new JsonObject().put("service-a", new JsonArray())))

    Thread.sleep(500)

    given()
      .when().get("/path")
      .`then`().statusCode(404)
  }
}
