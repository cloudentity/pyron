package com.cloudentity.edge.client

import com.cloudentity.edge.domain._
import com.cloudentity.edge.domain.flow.PathParams
import com.cloudentity.edge.domain.http.{FixedRelativeUri, QueryParams, UriPath}
import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RelativeUriSpec extends WordSpec with MustMatchers {
  "RelativeUri" should {
    "encode query params" in {
      FixedRelativeUri(UriPath("/path"), QueryParams("x" -> List("a a=")), PathParams(Map())).value must be("/path?x=a+a%3D")
    }
  }
}
