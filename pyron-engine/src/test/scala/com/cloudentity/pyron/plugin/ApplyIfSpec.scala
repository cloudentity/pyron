package com.cloudentity.pyron.plugin

import com.cloudentity.pyron.plugin.condition.{ApplyIf, Stringifiable}
import com.cloudentity.pyron.plugin.util.value.{HttpStatusRef, Value}
import com.cloudentity.pyron.test.TestRequestResponseCtx
import io.circe.Json
import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ApplyIfSpec extends WordSpec with MustMatchers with TestRequestResponseCtx {

  "ApplyIf.evaluate(Always, ...)" should {
    "return true" in {
      ApplyIf.evaluate(ApplyIf.Always, emptyRequestCtx) must be(true)
    }
  }

  "ApplyIf.evaluate(In(...), ...)" should {
    "return true if static string value in array" in {
      ApplyIf.evaluate(ApplyIf.In(List(Stringifiable("200"), Stringifiable("300")), Value(Json.fromString("200"))), emptyRequestCtx) must be(true)
    }
    "return false if static string value not in array" in {
      ApplyIf.evaluate(ApplyIf.In(List(Stringifiable("200"), Stringifiable("300")), Value(Json.fromString("400"))), emptyRequestCtx) must be(false)
    }
    "return true if response status in array" in {
      ApplyIf.evaluate(ApplyIf.In(List(Stringifiable("200")), HttpStatusRef), emptyResponseCtx.modifyResponse(_.withStatusCode(200))) must be(true)
    }
    "return false if response status not in array" in {
      ApplyIf.evaluate(ApplyIf.In(List(Stringifiable("500")), HttpStatusRef), emptyResponseCtx.modifyResponse(_.withStatusCode(200))) must be(false)
    }
  }
}
