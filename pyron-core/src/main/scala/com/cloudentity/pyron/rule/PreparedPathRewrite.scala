package com.cloudentity.pyron.rule

import com.cloudentity.pyron.domain.flow.PathParams

import scala.util.Try
import scala.util.matching.Regex


case class PreparedPathRewrite private(originPattern: String,
                                       matchPattern: String,
                                       pathPrefix: String,
                                       rewritePattern: String,
                                       paramNames: List[(String, Int)],
                                       rewriteMap: Map[String, Int],
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
    val pat = insertParamGroupsAndRefs(inputPattern, indexedParamNames)
    PreparedPathRewrite(
      originPattern = inputPattern,
      matchPattern = pat,
      pathPrefix = prefix,
      rewritePattern = outputPattern,
      paramNames = indexedParamNames,
      rewriteMap = getRewriteMap(outputPattern, indexedParamNames, totalGroupCount),
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
    pathParams = getPathParams(rewrite.rewriteMap, regexMatch)
    targetPath = rewritePath(pathParams, rewrite.rewritePattern)
  } yield AppliedPathRewrite(path, targetPath, pathParams, from = rewrite)

  private[rule] def rewritePath(pathParams: PathParams, rewritePattern: String): String = {
    pathParams.value.foldLeft(rewritePattern) {
      case (rew, (param, value)) => rew.replace(s"{$param}", value)
    }
  }

  private[rule] def getRewriteMap(rewritePattern: String, paramNames: List[(String, Int)], groupCount: Int): Map[String, Int] = {
    val numParamsUsedInRewrite = for {
      i <- (2 - groupCount) until groupCount
      (groupNum, paramNum) = if (i > 0) (i, i) else (groupCount + i - 1, i - 1)
      num <- Some(paramNum).filter(n => rewritePattern.contains(s"{$n}"))
    } yield s"$num" -> groupNum
    (numParamsUsedInRewrite ++ paramNames).toMap
  }

  private[rule] def getPathParams(rewriteParams: Map[String, Int], regexMatch: Regex.Match): PathParams = {
    PathParams(rewriteParams.mapValues(regexMatch.group))
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
                                             paramNamesWithGroupIndex: List[(String, Int)]): String = {
    paramNamesWithGroupIndex.foldLeft(pattern) {
      case (pat, (paramName, groupIndex)) =>
        replaceFirstAndRest(
          haystack = pat,
          needle = "{" + paramName + "}",
          replaceFirstWith = s"([^/]+)", // insert capture group for first or only occurrence
          replaceRestWith = s"\\$groupIndex" // insert back references for repeated occurrences
        )
    }
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
