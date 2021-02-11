package com.cloudentity.pyron.plugin.util.value

import scala.annotation.tailrec
import scala.util.matching.Regex

object PatternUtil {

  def safePatternAndParams(pattern: String): (Regex, List[String]) = {
    @tailrec
    def loop(sliceItAt: List[Int], isParamName: Boolean, slicesAcc: List[String], paramsAcc: List[String]): (Regex, List[String]) =
      sliceItAt match {
        case end :: start :: tail =>
          val (slices, params) = if (isParamName) {
            val paramDef = pattern.slice(start, end)
            val (paramName, paramMatch) = getParamMatch(paramDef)
            (paramMatch :: slicesAcc, paramName :: paramsAcc)
          } else {
            val otherThanParamName = pattern.slice(start, end)
            (escapeSymbols(normalizeParens(otherThanParamName)) :: slicesAcc, paramsAcc)
          }
          // 0-th and each even slice is non-param
          loop(start :: tail, !isParamName, slices, params)
        case _ => (("^" + slicesAcc.mkString + "$").r, paramsAcc)
      }

    loop(getSliceItAt(pattern), isParamName = false, slicesAcc = Nil, paramsAcc = Nil)
  }

  private def getSliceItAt(pattern: String): List[Int] = {
    // Find indexes delimiting non-param and param-def slices of the pattern
    val paramDef = """\{([a-zA-Z][a-zA-Z0-9]*(:?_[0-9]+){0,2}?)}"""
    val re = if (pattern.contains("{{")) {
      // We allow {{ and }} to match literal { and }, which makes finding params harder
      ("""(?:\G|[^{])(?:\{\{)*""" + paramDef).r
    } else {
      // Patterns without {{ and }} are likely much more common and simpler matcher will do
      paramDef.r
    }
    pattern.length :: re.findAllMatchIn(pattern)
      // Param def is captured into group(1)
      .foldLeft(List(0)) { case (acc, m) =>
        val openingCurlyBraceAt = m.end - m.group(1).length - 1
        val closingCurlyBraceAt = m.end - 1
        closingCurlyBraceAt :: openingCurlyBraceAt :: acc
      }
  }

  private def getParamMatch(paramDef: String): (String, String) = {
    paramDef.split('_').toList match {
      case paramName :: Nil => (paramName, s"(?<$paramName>.+)")
      case paramName :: matchSize :: Nil => (paramName, s"(?<$paramName>.{$matchSize})")
      case paramName :: minSize :: maxSize :: Nil =>
        assert(minSize < maxSize, s"Minimum must be smaller than maximum capture size but was: ($minSize, $maxSize)")
        (paramName, s"(?<$paramName>.{$minSize,$maxSize})")
      case _ => throw new Exception(s"Malformed param definition: [$paramDef]")
    }
  }

  private def escapeSymbols(normalizedParensSlice: String): String = {
    val escapeSymbolsRegex = """[.*+?^$()|{\[\\]""".r
    escapeSymbolsRegex.replaceAllIn(normalizedParensSlice, v => """\\\""" + v)
  }

  private def normalizeParens(nonParamSlice: String): String = {
    // this match will un-double all {{ and drop single/odd { parens
    val normalizeParensRegex = """([{}])(?<dualParen>\1?)""".r
    normalizeParensRegex.replaceAllIn(nonParamSlice, _.group("dualParen"))
  }

}
