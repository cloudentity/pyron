package com.cloudentity.edge.client

import com.cloudentity.edge.domain.flow.{ServiceClientName, SmartHttpClientConf}
import io.vertx.core.json.JsonObject
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}
import scalaz.\/-

@RunWith(classOf[JUnitRunner])
class SmartHttpConfsReaderSpec extends WordSpec with MustMatchers{
  "SmartHttpConfsReader" should {
    "return empty Map if config missing" in {
      SmartHttpConfsReader.readFromConfig(None, "") mustBe(\/-(Map()))
    }

    "return failure when not all configurations json-objects" in {
      val conf = new JsonObject().put("service-a", "wrong config")
      SmartHttpConfsReader.readFromConfig(Some(conf), "").isLeft mustBe(true)
    }

    "return Map with configs" in {
      val conf = new JsonObject().put("service-a", new JsonObject()).put("service-b", new JsonObject())

      val expected = Map(
        ServiceClientName("service-a") -> SmartHttpClientConf(new JsonObject()),
        ServiceClientName("service-b") -> SmartHttpClientConf(new JsonObject())
      )

      SmartHttpConfsReader.readFromConfig(Some(conf), "") mustBe(\/-(expected))
    }
  }
}
