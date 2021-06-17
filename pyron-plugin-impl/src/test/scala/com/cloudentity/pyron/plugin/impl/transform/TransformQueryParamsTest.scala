package com.cloudentity.pyron.plugin.impl.transform

import com.cloudentity.pyron.domain.http.QueryParams
import com.cloudentity.pyron.plugin.impl.transform.TransformQueryParams.setQueryParams
import com.cloudentity.pyron.test.TestRequestResponseCtx
import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TransformQueryParamsTest extends WordSpec with MustMatchers with TestRequestResponseCtx {

  "TransformQueryParams.set" should {

    "set value for non-existing query param" in {
      setQueryParams(Map("a" -> Option(List("x"))))(QueryParams.of()) mustBe QueryParams.of("a" -> "x")
    }

    "set multi-value for non-existing query param" in {
      setQueryParams(Map("a" -> Option(List("x", "y"))))(QueryParams.of()) mustBe QueryParams.of("a" -> "x", "a" -> "y")
    }

    "set value for existing query param" in {
      setQueryParams(Map("a" -> Option(List("x"))))(QueryParams.of("a" -> "y")) mustBe QueryParams.of("a" -> "x")
    }

    "set none value for non-existing query param" in {
      setQueryParams(Map("a" -> None))(QueryParams.of()) mustBe QueryParams.of()
    }

    "set none value for existing query param" in {
      setQueryParams(Map("a" -> None))(QueryParams.of("a" -> "x")) mustBe QueryParams()
    }
  }

}
