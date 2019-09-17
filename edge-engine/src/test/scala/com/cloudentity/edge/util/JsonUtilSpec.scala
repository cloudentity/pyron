package com.cloudentity.edge.util

import io.circe.Json
import io.circe.testing.ArbitraryInstances
import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.GeneratorDrivenPropertyChecks

@RunWith(classOf[JUnitRunner])
class JsonUtilSpec extends WordSpec with MustMatchers with ArbitraryInstances with GeneratorDrivenPropertyChecks {
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
