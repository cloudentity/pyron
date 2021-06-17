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
      val (regex, params) = PatternUtil.safePatternAndParams("{foo}/is/one/and{bar}is/an{other}")
      params mustBe List("foo", "bar", "other")
      regex.regex mustBe "^(?<foo>.+)/is/one/and(?<bar>.+)is/an(?<other>.+)$"
    }

    "build regex with min/max group size set" in {
      val (regex, params) = PatternUtil.safePatternAndParams("{foo:2}/is/one/and{bar:2:3}is/an{other:5:10}")
      params mustBe List("foo", "bar", "other")
      regex.regex mustBe "^(?<foo>.{2})/is/one/and(?<bar>.{2,3})is/an(?<other>.{5,10})$"
    }

    "allow using {{ and }} to match literal { and }, and ignore single { and } unless used to denote proper param name" in {
      val (regex, params) = PatternUtil.safePatternAndParams("{foo}/is/{{one}}?a{nd{bar}is/an{{other}}.d}ot[7]")
      params mustBe List("foo", "bar")
      regex.regex mustBe "^(?<foo>.+)/is/\\{one}\\?and(?<bar>.+)is/an\\{other}\\.dot\\[7]$"
    }

    "build regex which matches literally everything except named params, by properly escaping regex special chars" in {
      val (regex, params) = PatternUtil.safePatternAndParams("{foo}/is/one?question{bar}is/an{other}.dot[brackets]*star+plus()parens|pipe^caret")
      params mustBe List("foo", "bar", "other")
      regex.regex mustBe "^(?<foo>.+)/is/one\\?question(?<bar>.+)is/an(?<other>.+)\\.dot\\[brackets]\\*star\\+plus\\(\\)parens\\|pipe\\^caret$"
    }
  }
}
