package com.cloudentity.pyron.util

import io.circe.Json
import io.circe.testing.ArbitraryInstances
import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

@RunWith(classOf[JUnitRunner])
class JsonUtilSpec extends WordSpec with MustMatchers with ArbitraryInstances with ScalaCheckDrivenPropertyChecks {
  "JsonUtil.deepMerge" should {
    "preserve argument order" in forAll { (js: List[Json]) =>
      val fields = js.zipWithIndex.map {
        case (j, i) => i.toString -> j
      }

      val reversed = Json.fromFields(fields.reverse)
      val merged = JsonUtil.deepMerge(Json.fromFields(fields), reversed)

      assert(merged.asObject.map(_.toList.reverse) === Some(fields.reverse))
    }
  }
}
