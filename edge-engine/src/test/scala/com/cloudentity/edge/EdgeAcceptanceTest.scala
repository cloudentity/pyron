package com.cloudentity.edge

import com.cloudentity.tools.test.utils.VertxServerTest
import io.restassured.RestAssured
import io.vertx.core.json.JsonObject
import org.junit.Ignore

@Ignore
abstract class EdgeAcceptanceTest extends VertxServerTest {
  override def getMainVerticle: String = "com.cloudentity.edge.Application"

  override def configureRestAssured(metaConfJson: JsonObject): Unit = {
    RestAssured.reset()
    RestAssured.baseURI = "http://127.0.0.1"
    RestAssured.port = 8080
  }
}
