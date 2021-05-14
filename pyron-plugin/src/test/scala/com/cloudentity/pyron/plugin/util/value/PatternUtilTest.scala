package com.cloudentity.pyron.plugin.util.value

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class PatternUtilTest extends WordSpec with MustMatchers {

  "PatternUtil.safePatternAndParams" should {

    "extract param names from the pattern" in {
      val (_, params) = PatternUtil.safePatternAndParams("{foo}/is/one/and{bar}is/an{other}")
      params mustBe List("foo", "bar", "other")
    }

    "build regex by replacing param names with named capture groups" in {
      val (regex, _) = PatternUtil.safePatternAndParams("{foo}/is/one/and{bar}is/an{other}")
      regex.regex mustBe "^(?<foo>.+)/is/one/and(?<bar>.+)is/an(?<other>.+)$"
    }

    "build regex which matches literally by properly escaping regex special chars" in {
      val (regex, _) = PatternUtil.safePatternAndParams("{foo}/is/one?and{bar}is/an{other}.dot[7]")
      regex.regex mustBe "^(?<foo>.+)/is/one\\?and(?<bar>.+)is/an(?<other>.+)\\.dot\\[7]$"
    }
  }
}
