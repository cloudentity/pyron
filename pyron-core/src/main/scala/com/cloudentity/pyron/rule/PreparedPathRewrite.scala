package com.cloudentity.pyron.rule

import com.cloudentity.pyron.domain.flow.PathParams

import scala.util.Try
import scala.util.matching.Regex


case class PreparedPathRewrite private(originPattern: String,
                                       matchPattern: String,
                                       pathPrefix: String,
                                       rewritePattern: String,
                                       paramNames: List[(String, Int)],
                                       groupCount: Int) {

  val regexPattern: String = "^" + pathPrefix + matchPattern + "$" // anchor
  val regex: Regex = regexPattern.r // fails fast if regexPattern is invalid

  def applyRewrite(path: String): Option[AppliedPathRewrite] =
    PreparedPathRewrite.applyRewrite(path, this)

  def groupNum(param: String): Option[Int] =
    PreparedPathRewrite.groupNum(param, paramNames, groupCount)

  def paramName(num: Int): Option[String] =
    PreparedPathRewrite.paramName(num, paramNames)

}

object PreparedPathRewrite {

  private val PARAM_NAME_PATTERN = """[a-zA-Z_][a-zA-Z0-9_]*"""
  private val PLACEHOLDER_PATTERN = s"\\{$PARAM_NAME_PATTERN}"

  def rewritePathWithPathParams(rewritePath: String, pathParams: PathParams): String =
    pathParams.value.foldLeft(rewritePath) { case (path, (paramName, paramValue)) =>
      path.replace(s"{$paramName}", paramValue)
    }

  def prepare(inputPattern: String, prefix: String, outputPattern: String): Try[PreparedPathRewrite] = Try {
    val groupsCountPattern: String = getGroupsCountingPattern(inputPattern)
    val indexedParamNames: List[(String, Int)] = paramNamesWithGroupIndex(inputPattern, groupsCountPattern)
    val totalGroupCount = getCaptureGroupCount(groupsCountPattern) + indexedParamNames.size
    val (pat, rew) = insertParamGroupsAndRefs(inputPattern, outputPattern, indexedParamNames)
    PreparedPathRewrite(
      originPattern = inputPattern,
      matchPattern = pat,
      pathPrefix = prefix,
      rewritePattern = convertNegToPosNumericRefs(rew, totalGroupCount),
      paramNames = indexedParamNames,
      groupCount = totalGroupCount
    )
  }

  def paramName(num: Int, paramNames: List[(String, Int)]): Option[String] =
    paramNames.find(num == _._2).map(_._1)

  def groupNum(param: String, paramNames: List[(String, Int)], groupCount: Int): Option[Int] = {
    if (param.isEmpty) return None
    val notNumeric = param.charAt(0) > '9'
    if (notNumeric) {
      paramNames.find(param == _._1).map(_._2)
    } else try {
      val i = param.toInt
      if (i == 0 || groupCount / i == 0) None
      else if (i > 0) Some(i)
      else Some(i + groupCount + 1)
    } catch {
      case _: Throwable => None
    }
  }

  def applyRewrite(path: String, rewrite: PreparedPathRewrite): Option[AppliedPathRewrite] = for {
    regexMatch <- rewrite.regex.findFirstMatchIn(path)
    targetPath = rewritePath(rewrite.rewritePattern, regexMatch)
    pathParams = getPathParams(rewrite, regexMatch)
  } yield AppliedPathRewrite(path, targetPath, pathParams, from = rewrite)

  private[rule] def rewritePath(rewritePattern: String, regexMatch: Regex.Match): String =
    (0 to regexMatch.groupCount).map(i => (i, regexMatch.group(i))).foldLeft(rewritePattern) {
      case (rew, (idx, value)) => rew.replace(s"{$idx}", value)
    }

  private[rule] def getPathParams(rewrite: PreparedPathRewrite, regexMatch: Regex.Match): PathParams = {
    val namedParams = rewrite.paramNames.map { case (name, i) => name -> regexMatch.group(i)}.toMap
    PathParams(namedParams)
  }

  private[rule] def getCaptureGroupCount(groupsCountPattern: String): Int =
    groupsCountPattern.count(_ == '(') + 1 // entire regex counts as capture group too, hence +1

  private[rule] def getPrecedingGroupsCount(groupsCountPattern: String, index: Int): Int =
    groupsCountPattern.take(index).count(_ == '(') + 1 // entire regex counts as capture group too, hence +1

  private[rule] def getGroupsCountingPattern(pattern: String): String = pattern
    // any remaining '(' will be strictly these denoting beginning of a capture group
    .replaceAll("""\\{2}""", "") // remove literal '\'
    .replaceAll("""\[]?(?:[^]]|\\])*]""", "") // remove any char class
    .replaceAll("""\\\(""", "") // remove literal '('
    .replaceAll("""\(\?:""", "") // remove non-capture parens

  private[rule] def paramNamesWithGroupIndex(pattern: String, groupsCountingPattern: String): List[(String, Int)] = {
    val placeholders = PLACEHOLDER_PATTERN.r.findAllIn(pattern).toList.distinct
    placeholders.zipWithIndex.map {
      case (placeholder, idx) =>
        val placeholderFoundAt = groupsCountingPattern.indexOf(placeholder)
        val paramGroupIndex = getPrecedingGroupsCount(groupsCountingPattern, placeholderFoundAt) + idx
        (makeParamName(placeholder), paramGroupIndex)
    }
  }

  private[rule] def insertParamGroupsAndRefs(pattern: String,
                                             rewrite: String,
                                             paramNamesWithGroupIndex: List[(String, Int)]): (String, String) = {
    paramNamesWithGroupIndex.foldLeft((pattern, rewrite)) {
      case ((p, r), (paramName, groupIndex)) =>
        val paramPlaceholder = "{" + paramName + "}"
        val pat = replaceFirstAndRest(
          haystack = p,
          needle = paramPlaceholder,
          replaceFirstWith = s"([^/]+)", // insert capture group for first or only occurrence
          replaceRestWith = s"\\$groupIndex" // insert back references for repeated occurrences
        )
        val rew = r.replace(paramPlaceholder, s"{$groupIndex}")
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

  private[rule] def makeParamName(placeholder: String): String = placeholder.slice(1, placeholder.length - 1)

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