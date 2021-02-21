package com.cloudentity.pyron.rule

import scala.util.Try

object RewriteUtil {

  def applyRewrite(pattern: String, rewrite: String, path: String): Option[String] = {
    val (pat, rew) = finalPatternAndRewrite(pattern, rewrite)
    pat.r.findFirstMatchIn(path)
      .map(m => (0 to m.groupCount).map(v => (v, m.group(v))).foldLeft(rew) {
        case (rew, (idx, value)) => rew.replace(s"{$idx}", value)
      })
  }

  def finalPatternAndRewrite(pattern: String, rewrite: String): (String, String) = {
    val parensCountPattern = parensCountingPattern(pattern)
    val indexedParamPlaceholders = paramPlaceholdersWithGroupIndex(pattern, parensCountPattern)
    val totalGroupsCount = 1 + parensCountPattern.count(_ == '(') + indexedParamPlaceholders.size
    val (pat, rew) = insertParamGroupsAndRefs(pattern, rewrite, indexedParamPlaceholders)
    (s"^$pat$$", convertNegToPosNumericRefs(rew, totalGroupsCount))
  }

  def parensCountingPattern(pattern: String): String = pattern
    .replaceAll("""\\{2}""", "") // remove literal '\'
    .replaceAll("""\\\(""", "") // remove literal '('
    .replaceAll("""\(\?:""", "") // remove non-capture parens
    .replaceAll("""\[]?(?:[^]]|\\])*]""", "") // remove any char class

  def paramPlaceholdersWithGroupIndex(pattern: String, parensCountingPattern: String): List[(String, Int)] = {
    val placeholders = """\{[a-zA-Z_][a-zA-Z0-9_]*}""".r.findAllIn(pattern).toList.distinct
    placeholders.zipWithIndex.map {
      case (placeholder, idx) =>
        val placeholderFoundAt = parensCountingPattern.indexOf(placeholder)
        val paramGroupIndex = parensCountingPattern
          .slice(0, placeholderFoundAt)
          .count(_ == '(') + idx + 1
        (placeholder, paramGroupIndex)
    }
  }

  def convertNegToPosNumericRefs(rewrite: String, totalGroupsCount: Int): String = {
    // Replace negative numeric refs, which allow to count groups from the right-side/end of the pattern
    // with positive numeric refs pointing to the same group from the left-side/start of the pattern.
    // Numeric refs will now match the capture group numbers of the pattern regex for easy value retrieval.
    """\{-\d+}""".r.replaceSomeIn(rewrite, m => Try {
      m.matched.slice(2, m.matched.length - 1).toInt
    }.toOption.flatMap {
      i => if (i > totalGroupsCount) None else Some(s"{${totalGroupsCount - i}}")
    })
  }

  def insertParamGroupsAndRefs(pattern: String,
                               rewrite: String,
                               paramPlaceholdersWithGroupIndex: List[(String, Int)]): (String, String) = {
    paramPlaceholdersWithGroupIndex.foldLeft((pattern, rewrite)) {
      case ((p, r), (placeholder, groupIndex)) =>
        val pat = replaceFirstAndRest(
          haystack = p,
          needle = placeholder,
          replaceFirstWith = s"([^/]+)", // insert capture group for first or only occurrence
          replaceRestWith = s"\\$groupIndex" // insert back references for repeated occurrences
        )
        val paramName = placeholder.slice(1, placeholder.length - 1)
        val rew = r.replace(s"{$paramName}", s"{$groupIndex}")
        (pat, rew)
    }
  }

  def replaceFirstAndRest( haystack: String,
                           needle: String,
                           replaceFirstWith: String,
                           replaceRestWith: String
                         ): String = {
    val foundAt = haystack.indexOf(needle)
    val before = haystack.substring(0, foundAt)
    val after = haystack.substring(foundAt + needle.length)
    before + replaceFirstWith + after.replace(needle, replaceRestWith)
  }

}
