package com.cloudentity.pyron.rule

import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatestplus.junit.JUnitRunner
import RewriteUtil._
import com.cloudentity.pyron.domain.flow.PathParams

import scala.util.Try

@RunWith(classOf[JUnitRunner])
class RewriteUtilTest extends WordSpec with MustMatchers {

  "rewritePathWithPathParams" should {
    "replace param references within path with param values" in {
      rewritePathWithPathParams("/some/{one}/path/{two}/{one}/", PathParams(Map("one" -> "UNO", "two" -> "DUO"))) mustBe
      "/some/UNO/path/DUO/UNO/"
    }
  }

  "prepareRewrite" should {
    "fail to create prepared rewrite when pattern is not a valid regex pattern" in {
      Try(prepareRewrite("/pattern/with(unbalanced(parens)/should/{param}/fail", "/api", "/resources/{param}/{1}")).isFailure mustBe true
    }
    "succeed in creating prepared rewrite when pattern is a valid regex pattern" in {
      val tryPreparedRewrite = Try(prepareRewrite(
        "/pattern/with(balanced(parens))/should/{one}/be/{two}/ok/{two}",
        "/api",
        "/resources/{two}/{one}/{1}")
      )
      tryPreparedRewrite.isSuccess mustBe true
      val preparedRewrite = tryPreparedRewrite.get
      preparedRewrite.checkedPattern mustBe "/pattern/with(balanced(parens))/should/([^/]+)/be/([^/]+)/ok/\\4"
      preparedRewrite.pathPrefix mustBe "/api"
      preparedRewrite.rewritePattern mustBe "/resources/{4}/{3}/{1}"
      preparedRewrite.indexedParamPlaceholders mustBe List(("{one}", 3), ("{two}", 4))
    }
  }

  "rewritePath" should {
    "produce rewritten path with resolved params, captured by the matching pattern" in {
      val preparedRewrite = prepareRewrite(
        "/pattern/with_(balanced_(parens))/should/{one}/be/{two}/ok/{two}",
        "/api",
        "/resources/{two}/{one}/{1}")
      rewritePathWithPreparedRewrite(preparedRewrite, "/api/pattern/with_balanced_parens/should/UNO/be/DOS/ok/DOS") mustBe
        Some("/resources/DOS/UNO/balanced_parens")
    }
    "not produce rewritten path when the pattern does not match" in {
      val preparedRewrite = prepareRewrite(
        "/pattern/with_(balanced_(parens))/should/{one}/be/{two}/ok/{two}",
        "/api",
        "/resources/{two}/{one}/{1}")
      rewritePathWithPreparedRewrite(preparedRewrite, "/api/pattern/with_balanced_parens/should/UNO/be/DOS/ok/TRES") mustBe None
    }
  }

  "getCaptureGroupsCount" should {
    "count capture groups occurring within pattern by counting all opening parens" in {
      getCaptureGroupsCount("here we have only implicit capture group which matches entire pattern") mustBe 1
      getCaptureGroupsCount("some(.*)stuff(capture_this(\\d+)is_here(capture_that)") mustBe 5
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

  "paramsPlaceholderWithGroupIndex" should {
    "extract named params with corresponding capture group number" in {
      val pattern = "(this){stuff}(.*)is(.+)inter(nal|est{ing})"
      val res = paramPlaceholdersWithGroupIndex(pattern, getGroupsCountingPattern(pattern))
      res mustBe List(("{stuff}", 2), ("{ing}", 6))
    }
    "extract only first occurrence of repeated param placeholders" in {
      val pattern = "(this){stuff}(.*)is(.+){stuff}inter(nal|est{ing}){ing}{stuff}"
      val res = paramPlaceholdersWithGroupIndex(pattern, getGroupsCountingPattern(pattern))
      res mustBe List(("{stuff}", 2), ("{ing}", 6))
    }
    "extract only valid param placeholders" in {
      // valid param placeholder has non-empty param name surrounded by curly braces, the name may
      // only contain underscore, small/big latin letters and digits, but can't start with a digit
      val pattern = "/{ValId_Id}{not an id}/(anon_group_one)/{_1_ok_2_}/(anon_group_two)" ++
        "{9_starts_with_a_digit_not_an_id}/{}{żółć}{this_ONE_is_valid_ID}"
      // if chunk within curly braces is not valid param name, it won't be used or interpreted
      val res = paramPlaceholdersWithGroupIndex(pattern, getGroupsCountingPattern(pattern))
      res mustBe List(("{ValId_Id}", 1), ("{_1_ok_2_}", 3), ("{this_ONE_is_valid_ID}", 5))
    }
  }

  "convertNegToPosNumericRefs" should {
    "replace all negative numeric references with a positive numeric references" in {
      convertNegToPosNumericRefs("rewrite {-3} has some negative {-1} and positive {2} numeric refs {-2}", 4) mustBe
        "rewrite {1} has some negative {3} and positive {2} numeric refs {2}"
    }
  }

  "insertParamGroupsAndRefs" should {
    val pattern = "Grand, but (how)/{com}e/I/ca(nn)ot/underst{and}/why/I/have/two/h{and}s/{and}/two/legs!"
    val rewrite = "/hello/{and}/wel{com}e/to-the-bra{and}-new/gr{and}-coin-toss-{com}petition"
    val (pat, rew) = insertParamGroupsAndRefs(pattern, rewrite, List(("{com}", 2), ("{and}", 5)))
    "replace first occurrence of param with capture group and remaining occurrences with back-references" in {
      pat mustBe """Grand, but (how)/([^/]+)e/I/ca(nn)ot/underst([^/]+)/why/I/have/two/h\5s/\5/two/legs!"""
    }
    "replace all named param references in rewrite with numeric references" in {
      rew mustBe """/hello/{5}/wel{2}e/to-the-bra{5}-new/gr{5}-coin-toss-{2}petition"""
    }
  }

  "getParamName" should {
    "get param name from placeholder string" in {
      val placeholder = "{some_param_name}"
      RewriteUtil.getParamName(placeholder) mustBe "some_param_name"
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


}
