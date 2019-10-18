package com.cloudentity.pyron.rule

import com.cloudentity.pyron.domain.flow.{PathMatching, PathPattern}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class PathMatchingSpec extends WordSpec with MustMatchers {
  "PathMatching.createPatternRegex" should {
    "wrap pattern in ^ and $ regex symbols" in {
      PathMatching.createPatternRegex("/path").regex must be("^/path$")
    }
    "substitute placeholder with [^/]+ regex" in {
      PathMatching.createPatternRegex("/path/{placeholder}/path").regex must be("^/path/(?<placeholder>[^/]+)/path$")
    }
  }
  "PathMatching.extractPathParamNames" should {
    val extract = PathMatching.extractPathParamNames _

    "extract no names" in {
      // given
      val path = PathPattern("/path/path")

      // when
      val names = extract(path)

      // then
      names.map(_.value) must be(Nil)
    }

    "extract single name" in {
      // given
      val path = PathPattern("/path/{placeholder}/path")

      // when
      val names = extract(path)

      // then
      names.map(_.value) must be(List("placeholder"))
    }

    "extract multiple names" in {
      // given
      val path = PathPattern("/path/{placeholder1}/path/{placeholder2}")

      // when
      val names = extract(path)

      // then
      names.map(_.value) must be(List("placeholder1", "placeholder2"))
    }
  }
}
