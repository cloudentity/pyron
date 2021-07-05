package com.cloudentity.pyron.rule

import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatestplus.junit.JUnitRunner
import PreparedPathRewrite._
import com.cloudentity.pyron.domain.flow.PathParams

@RunWith(classOf[JUnitRunner])
class PreparedPathRewriteTest extends WordSpec with MustMatchers {

  "PreparedPathRewrite" should {

    "fail to create prepared rewrite when pattern is not a valid regex pattern" in {
      PreparedPathRewrite.prepare(
        "/pattern/with(unbalanced(parens)/should/{param}/fail",
        "/api",
        "/resources/{param}/{1}"
      ).isFailure mustBe true
    }

    "succeed in creating prepared rewrite when pattern is a valid regex pattern" in {
      val tryPreparedRewrite = PreparedPathRewrite.prepare(
        "/pattern/with(balanced(parens))/should/{one}/be/{two}/ok/{two}",
        "/api",
        "/resources/{two}/{one}/{1}")
      tryPreparedRewrite.isSuccess mustBe true
      val preparedRewrite = tryPreparedRewrite.get
      preparedRewrite.matchPattern mustBe "/pattern/with(balanced(parens))/should/([^/]+)/be/([^/]+)/ok/\\4"
      preparedRewrite.pathPrefix mustBe "/api"
      preparedRewrite.rewritePattern mustBe "/resources/{two}/{one}/{1}"
      preparedRewrite.namedParams mustBe List(("one", 3), ("two", 4))
      preparedRewrite.numericRefs mustBe List("1" -> 1)
      preparedRewrite.rewriteMap mustBe Map("one" -> 3, "two" -> 4, "1" -> 1)
    }

    "collect only referenced (positive) numeric path params and all named params" in {
      val tryPreparedRewrite = PreparedPathRewrite.prepare(
        "/pattern/(A)(B)/{namedParamC}/(D)/and-the-rest-(.*)",
        "/api",
        "/pattern/{1}/stuff/{2}/{4}")
      tryPreparedRewrite.isSuccess mustBe true
      val preparedRewrite = tryPreparedRewrite.get
      preparedRewrite.namedParams mustBe List("namedParamC" -> 3)
      preparedRewrite.numericRefs mustBe List("1" -> 1, "2" -> 2, "4" -> 4)
      preparedRewrite.rewriteMap mustBe Map("1" -> 1, "2" -> 2, "namedParamC" -> 3,  "4" -> 4)

      preparedRewrite.applyRewrite("/api/pattern/AB/valueC/D/and-the-rest-Foo/Bar")
        .map(_.targetPath) mustBe Some("/pattern/A/stuff/B/D")
    }

    "collect only referenced (negative) numeric path params and all named params" in {
      val tryPreparedRewrite = PreparedPathRewrite.prepare(
        "/pattern/(A)(B)/{namedParamC}/(D)/and-the-rest-(.*)",
        "/api",
        "/pattern/{-1}/stuff/{-2}/{-4}")
      tryPreparedRewrite.isSuccess mustBe true
      val preparedRewrite = tryPreparedRewrite.get
      preparedRewrite.namedParams mustBe List("namedParamC" -> 3)
      preparedRewrite.numericRefs mustBe List("-4" -> 2, "-2" -> 4, "-1" -> 5)
      preparedRewrite.rewriteMap mustBe Map("-1" -> 5, "-2" -> 4, "namedParamC" -> 3,  "-4" -> 2)

      preparedRewrite.applyRewrite("/api/pattern/AB/valueC/D/and-the-rest-Foo/Bar")
        .map(_.targetPath) mustBe Some("/pattern/Foo/Bar/stuff/D/B")
    }

    "collect only referenced (negative and positive) numeric path params, and all named params" in {
      val tryPreparedRewrite = PreparedPathRewrite.prepare(
        "/pattern/(A)(B)/{namedParamC}/(D)/and-the-rest-(.*)",
        "/api",
        "/pattern/{1}/stuff/{3}/{-1}/and/{-2}/{-5}")
      tryPreparedRewrite.isSuccess mustBe true
      val preparedRewrite = tryPreparedRewrite.get
      preparedRewrite.namedParams mustBe List("namedParamC" -> 3)
      preparedRewrite.numericRefs mustBe List("-5" -> 1, "-2" -> 4, "-1" -> 5, "1" -> 1, "3" -> 3)
      preparedRewrite.rewriteMap mustBe Map("1" -> 1, "-5" -> 1, "3" -> 3, "namedParamC" -> 3, "-2" -> 4,  "-1" -> 5)

      preparedRewrite.applyRewrite("/api/pattern/AB/valueC/D/and-the-rest-Foo/Bar")
        .map(_.targetPath) mustBe Some("/pattern/A/stuff/valueC/Foo/Bar/and/D/A")
    }

  }

  "getCaptureGroupCount" should {
    "count capture groups occurring within pattern by counting all opening parens" in {
      getCaptureGroupCount("here we have only implicit capture group which matches entire pattern") mustBe 1
      getCaptureGroupCount("some(.*)stuff(capture_this(\\d+)is_here(capture_that)") mustBe 5
    }
  }

  "getPrecedingGroupsCount" should {
    "count capture groups occurring within pattern before index by counting preceding opening parens" in {
      val pattern = "(.*)foo(8_capture_this(23\\d+)is_here(37_capture_that)"
      getPrecedingGroupsCount(pattern, 0) mustBe 1
      getPrecedingGroupsCount(pattern, 1) mustBe 2
      getPrecedingGroupsCount(pattern, 7) mustBe 2
      getPrecedingGroupsCount(pattern, 8) mustBe 3
      getPrecedingGroupsCount(pattern, 22) mustBe 3
      getPrecedingGroupsCount(pattern, 23) mustBe 4
      getPrecedingGroupsCount(pattern, 36) mustBe 4
      getPrecedingGroupsCount(pattern, 37) mustBe 5
      getPrecedingGroupsCount(pattern, pattern.length) mustBe 5
    }
  }

  "getGroupsCountingPattern" should {
    "discard all double slashes, but keep single slash when there are odd number of occurrences in a row" in {
      getGroupsCountingPattern("""discard double\\ \\occurrences and also quadruple\\\\ but keep single one\ or one from odd number of occurrences\\\?""") mustBe
        """discard double occurrences and also quadruple but keep single one\ or one from odd number of occurrences\?"""
    }
    "discard any character classes, which potentially may contain '(' which would not denote a capture group" in {
      getGroupsCountingPattern("""make sure [any] [a(n)d] all char classes [?&)A-Z] are gone""") mustBe
        "make sure   all char classes  are gone"
    }
    "discard any literal parens" in {
      getGroupsCountingPattern("""here was some \(literal paren\) and here \\\(another one and then a \\(proper capture group)""") mustBe
        """here was some literal paren\) and here another one and then a (proper capture group)"""
    }
    "discard any non-capture group's starting sequence" in {
      getGroupsCountingPattern("""here is some (?:non_capture_group) and here (a capture group) and(?: non capture again)""") mustBe
        """here is some non_capture_group) and here (a capture group) and non capture again)"""
    }
  }

  "paramNamesWithGroupIndex" should {
    "extract named params with corresponding capture group number" in {
      val pattern = "(this){stuff}(.*)is(.+)inter(nal|est{ing})"
      val res = paramNamesWithGroupIndex(pattern, getGroupsCountingPattern(pattern))
      res mustBe List(("stuff", 2), ("ing", 6))
    }
    "extract only first occurrence of repeated param placeholders" in {
      val pattern = "(this){stuff}(.*)is(.+){stuff}inter(nal|est{ing}){ing}{stuff}"
      val res = paramNamesWithGroupIndex(pattern, getGroupsCountingPattern(pattern))
      res mustBe List(("stuff", 2), ("ing", 6))
    }
    "extract only valid param placeholders" in {
      // valid param placeholder has non-empty param name surrounded by curly braces, the name may
      // only contain underscore, small/big latin letters and digits, but can't start with a digit
      val pattern = "/{ValId_Id}{not an id}/(anon_group_one)/{_1_ok_2_}/(anon_group_two)" ++
        "{9_starts_with_a_digit_not_an_id}/{}{żółć}{this_ONE_is_valid_ID}"
      // if chunk within curly braces is not valid param name, it won't be used or interpreted
      val res = paramNamesWithGroupIndex(pattern, getGroupsCountingPattern(pattern))
      res mustBe List(("ValId_Id", 1), ("_1_ok_2_", 3), ("this_ONE_is_valid_ID", 5))
    }
  }

  "insertParamGroupsAndRefs" should {
    val pattern = "Grand, but (how)/{com}e/I/ca(nn)ot/underst{and}/why/I/have/two/h{and}s/{and}/two/legs!"
    val pat = insertParamGroupsAndRefs(pattern, List(("com", 2), ("and", 5)))
    "replace first occurrence of param with capture group and remaining occurrences with back-references" in {
      pat mustBe """Grand, but (how)/([^/]+)e/I/ca(nn)ot/underst([^/]+)/why/I/have/two/h\5s/\5/two/legs!"""
    }
  }

  "makeParamName" should {
    "get param name from placeholder string" in {
      val placeholder = "{some_param_name}"
      makeParamName(placeholder) mustBe "some_param_name"
    }
  }

  "replaceFirstAndRest" should {
    "replace first occurrence and all remaining occurrences with replacements provided" in {
      replaceFirstAndRest(
        haystack = "abcxyz bar boo baz abcxyz goo blah abcxyz",
        needle = "abcxyz",
        replaceFirstWith = "ABC",
        replaceRestWith = "XYZ"
      ) mustBe "ABC bar boo baz XYZ goo blah XYZ"
    }
  }

  "rewritePathWithPathParams" should {
    "replace param references within path with param values" in {
      rewritePathWithPathParams("/some/{one}/path/{two}/{one}/", PathParams(Map("one" -> "UNO", "two" -> "DUO"))) mustBe
        "/some/UNO/path/DUO/UNO/"
    }
  }

  "produce rewritten path with resolved params, captured by the matching pattern" in {
    val preparedRewriteOpt = PreparedPathRewrite.prepare(
      "/pattern/with_(balanced_(parens))/should/{one}/be/{two}/ok/{two}",
      "/api",
      "/resources/{two}/{one}/{1}").toOption

    val result = for {
      preparedRewrite <- preparedRewriteOpt
      regexMatch <- preparedRewrite.regex.findFirstMatchIn("/api/pattern/with_balanced_parens/should/UNO/be/DOS/ok/DOS")
      pathParams = getPathParams(preparedRewrite.rewriteMap, regexMatch)
    } yield rewritePath(pathParams, preparedRewrite.rewritePattern)
    result mustBe Some("/resources/DOS/UNO/balanced_parens")
  }

  "not produce rewritten path when the pattern does not match" in {
    val preparedRewriteOpt = PreparedPathRewrite.prepare(
      "/pattern/with_(balanced_(parens))/should/{one}/be/{two}/ok/{two}",
      "/api",
      "/resources/{two}/{one}/{1}").toOption

    val result = for {
      preparedRewrite <- preparedRewriteOpt
      regexMatch <- preparedRewrite.regex.findFirstMatchIn("/api/pattern/with_balanced_parens/should/UNO/be/DOS/ok/TRES")
      pathParams = getPathParams(preparedRewrite.rewriteMap, regexMatch)
    } yield rewritePath(pathParams, preparedRewrite.rewritePattern)
    result mustBe None
  }

}
