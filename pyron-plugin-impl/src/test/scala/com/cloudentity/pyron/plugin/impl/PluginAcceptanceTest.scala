package com.cloudentity.pyron.plugin.impl

import com.cloudentity.tools.vertx.test.VertxServerTest
import io.restassured.RestAssured
import io.vertx.core.json.JsonObject
import org.junit.Ignore

@Ignore
abstract class PluginAcceptanceTest extends VertxServerTest {
  override def getMainVerticle: String = "com.cloudentity.pyron.Application"

  override def configureRestAssured(metaConfJson: JsonObject): Unit = {
    RestAssured.reset()
    RestAssured.baseURI = "http://127.0.0.1"
    RestAssured.port = 8080
  }
}
