package com.cloudentity.edge

import java.nio.file.{Files, Paths}

import com.cloudentity.tools.test.utils.VertxServerTest
import com.cloudentity.tools.vertx.conf.retriever.ConfigRetrieverConf
import com.cloudentity.tools.vertx.configs.ConfigFactory
import com.cloudentity.tools.vertx.json.VertxJson
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import io.restassured.RestAssured
import io.vertx.core.json.JsonObject
import org.junit.{After, Ignore}

@Ignore
abstract class ApiGatewayTest extends VertxServerTest {
  override def getMainVerticle: String = "com.cloudentity.edge.Application"
  override def getMetaConfPath() = "src/test/resources/meta-config.json"

  implicit lazy val ec = VertxExecutionContext(getVertx.getOrCreateContext())

  @After
  def sleepAWhile(): Unit = {
    // this sleep prevents tests to fail randomly in maven when all run...
    Thread.sleep(200)
  }

  override def configureRestAssured(metaConfJson: JsonObject): Unit = {
    VertxJson.registerJsonObjectDeserializer()

    val metaConf = ConfigFactory.build(metaConfJson, classOf[ConfigRetrieverConf])
    val configPath = metaConf.getStores.get(0).getConfig.getString("path")

    val appConf = new JsonObject(new String(Files.readAllBytes(Paths.get(configPath)))).getJsonObject("orchis").getJsonObject("app")
    val port = appConf.getInteger("port", 8080)

    RestAssured.reset()
    RestAssured.baseURI = "http://127.0.0.1"
    RestAssured.port = port
  }
}
