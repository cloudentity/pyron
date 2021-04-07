package com.cloudentity.pyron.rule

import com.cloudentity.pyron.domain.flow.PathParams

import scala.annotation.tailrec
import scala.util.Try
import scala.util.matching.Regex

object RewriteUtil {

  val PARAM_NAME_PATTERN = """[a-zA-Z_][a-zA-Z0-9_]*"""
  val PLACEHOLDER_PATTERN = s"\\{$PARAM_NAME_PATTERN}"

  def rewritePathWithPathParams(rewritePath: String, pathParams: PathParams): String =
    pathParams.value.foldLeft(rewritePath) { case (path, (paramName, paramValue)) =>
      path.replace(s"{$paramName}", paramValue)
    }

  def prepareRewrite(pattern: String, prefix: String, rewrite: String): PreparedRewrite = {
    val groupsCountPattern: String = getGroupsCountingPattern(pattern)
    val indexedParamPlaceholders: List[(String, Int)] = paramPlaceholdersWithGroupIndex(pattern, groupsCountPattern)
    val totalParamsCount: Int = getCaptureGroupsCount(groupsCountPattern) + indexedParamPlaceholders.size
    val (pat, rew) = insertParamGroupsAndRefs(pattern, rewrite, indexedParamPlaceholders)
    PreparedRewrite(
      pathPrefix = prefix,
      checkedPattern = pat,
      rewritePattern = convertNegToPosNumericRefs(rew, totalParamsCount),
      indexedParamPlaceholders = indexedParamPlaceholders)
  }

  def applyRewrite(path: String, rewrite: PreparedRewrite): Option[AppliedRewrite] = for {
    regexMatch <- rewrite.regex.findFirstMatchIn(path)
    targetPath = rewritePathWithPreparedRewrite(rewrite.rewritePattern, regexMatch)
    pathParams = extractPathParams(rewrite.indexedParamPlaceholders, regexMatch)
  } yield AppliedRewrite(path, targetPath, pathParams, from = rewrite)

  private[rule] def rewritePathWithPreparedRewrite(rewritePattern: String, regexMatch: Regex.Match): String =
    (0 to regexMatch.groupCount).map(i => (i, regexMatch.group(i))).foldLeft(rewritePattern) {
      case (rew, (idx, value)) => rew.replace(s"{$idx}", value)
    }

  private[rule] def extractPathParams(paramPlaceholders: List[(String,Int)], regexMatch: Regex.Match) = {
    val namedParams = paramPlaceholders.map {
      case (placeholder, idx) => (getParamName(placeholder), regexMatch.group(idx))
    }
    PathParams((namedParams ::: getNumericParams(regexMatch)).toMap)
  }

  private[rule] def getNumericParams(regexMatch: Regex.Match): List[(String, String)] = {
    @tailrec
    def loop(idx: Int, acc: List[(String, String)]): List[(String, String)] = if (idx > 0) {
      val (posIdx, negIdx, groupAtIdx) = (s"$idx", s"${idx - regexMatch.groupCount - 1}", regexMatch.group(idx))
      loop(idx - 1,  (posIdx, groupAtIdx) :: (negIdx, groupAtIdx) :: acc)
    } else {
      acc
    }
    loop(regexMatch.groupCount, Nil)
  }

  private[rule] def getCaptureGroupsCount(groupsCountPattern: String): Int =
    groupsCountPattern.count(_ == '(') + 1 // entire regex counts as capture group too, hence +1

  private[rule] def getPrecedingGroupsCount(groupsCountPattern: String, index: Int): Int =
    groupsCountPattern.take(index).count(_ == '(') + 1 // entire regex counts as capture group too, hence +1

  private[rule] def getGroupsCountingPattern(pattern: String): String = pattern
    // any remaining '(' will be strictly these denoting beginning of a capture group
    .replaceAll("""\\{2}""", "") // remove literal '\'
    .replaceAll("""\[]?(?:[^]]|\\])*]""", "") // remove any char class
    .replaceAll("""\\\(""", "") // remove literal '('
    .replaceAll("""\(\?:""", "") // remove non-capture parens

  private[rule] def paramPlaceholdersWithGroupIndex(pattern: String, groupsCountingPattern: String): List[(String, Int)] = {
    val placeholders = PLACEHOLDER_PATTERN.r.findAllIn(pattern).toList.distinct
    placeholders.zipWithIndex.map {
      case (placeholder, idx) =>
        val placeholderFoundAt = groupsCountingPattern.indexOf(placeholder)
        val paramGroupIndex = getPrecedingGroupsCount(groupsCountingPattern, placeholderFoundAt) + idx
        (placeholder, paramGroupIndex)
    }
  }

  private[rule] def insertParamGroupsAndRefs(pattern: String,
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

  private[rule] def convertNegToPosNumericRefs(rewrite: String, totalGroupsCount: Int): String = {
    // Replace negative numeric refs, which allow to count groups from the right-side/end of the pattern
    // with positive numeric refs pointing to the same group from the left-side/start of the pattern.
    // Numeric refs will now match the capture group numbers of the pattern regex for easy value retrieval.
    """\{-\d+}""".r.replaceSomeIn(rewrite, m => Try {
      m.matched.slice(2, m.matched.length - 1).toInt
    }.toOption.flatMap {
      i => if (i > totalGroupsCount) None else Some(s"{${totalGroupsCount - i}}")
    })
  }

  private[rule] def getParamName(placeholder: String): String = placeholder.slice(1, placeholder.length - 1)

  private[rule] def replaceFirstAndRest(haystack: String,
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
