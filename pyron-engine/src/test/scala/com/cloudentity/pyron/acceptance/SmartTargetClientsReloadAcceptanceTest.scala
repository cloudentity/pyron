package com.cloudentity.pyron.acceptance

import com.cloudentity.pyron.PyronAcceptanceTest
import com.cloudentity.pyron.util.MockUtils
import io.restassured.RestAssured.given
import io.vertx.core.json.JsonObject
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpResponse

class SmartTargetClientsReloadAcceptanceTest extends PyronAcceptanceTest with MockUtils {
  override def getMetaConfPath(): String = "src/test/resources/acceptance/smart-clients-reload/meta-config.json"

  var targetServiceA: ClientAndServer = null
  var targetServiceB: ClientAndServer = null

  @Before
  def before(): Unit = {
    targetServiceA = startClientAndServer(7777)
    targetServiceB = startClientAndServer(7778)
  }

  @After
  def finish(): Unit = {
    targetServiceA.stop
    targetServiceB.stop
  }

  @Test
  def shouldReloadSmartHttpTargetClients(): Unit = {
    mockOnPath(targetServiceA)("/path", HttpResponse.response().withStatusCode(201))
    mockOnPath(targetServiceB)("/path", HttpResponse.response().withStatusCode(401))

    given()
      .when().get("/path")
      .`then`().statusCode(201)

    val smartClientsConfig = new JsonObject().put("service-a", new JsonObject().put("serviceName", "service-b"))

    vertx.eventBus().publish("reload-events-address", new JsonObject().put("smart-http-target-clients", smartClientsConfig))

    Thread.sleep(500)

    given()
      .when().get("/path")
      .`then`().statusCode(401)
  }

}
