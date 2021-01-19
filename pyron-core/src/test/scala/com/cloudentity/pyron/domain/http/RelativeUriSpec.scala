package com.cloudentity.pyron.domain.http

import com.cloudentity.pyron.domain.flow.PathParams
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class RelativeUriSpec extends WordSpec with MustMatchers {
  "RelativeUri" should {
    "encode query params" in {
      FixedRelativeUri(UriPath("/path"), QueryParams("x" -> List("a a=")), PathParams(Map())).value must be("/path?x=a+a%3D")
    }
  }
}
