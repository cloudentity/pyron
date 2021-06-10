package com.cloudentity.pyron.plugin.impl.transform

import com.cloudentity.pyron.domain.flow.PathParams
import com.cloudentity.pyron.plugin.impl.transform.TransformPathParams.setPathParams
import com.cloudentity.pyron.test.TestRequestResponseCtx
import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TransformPathParamsTest extends WordSpec with MustMatchers with TestRequestResponseCtx {

  "TransformPathParams.set" should {

    "set value for non-existing param" in {
      setPathParams(Map("a" -> Option("string")))(PathParams(Map())) mustBe PathParams(Map("a" -> "string"))
    }

    "set value for existing param" in {
      setPathParams(Map("a" -> Option("string")))(PathParams(Map("a" -> "x"))) mustBe PathParams(Map("a" -> "string"))
    }

    "set none value for non-existing param" in {
      setPathParams(Map("a" -> None))(PathParams(Map())) mustBe PathParams(Map())
    }

    "set none value for existing param" in {
      setPathParams(Map("a" -> None))(PathParams(Map("a" -> "x"))) mustBe PathParams(Map())
    }
  }

}
