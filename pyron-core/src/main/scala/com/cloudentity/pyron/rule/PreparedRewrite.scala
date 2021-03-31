package com.cloudentity.pyron.rule

import scala.util.matching.Regex

case class PreparedRewrite(pathPrefix: String,
                           checkedPattern: String,
                           rewritePattern: String,
                           indexedParamPlaceholders: List[(String, Int)]) {
  val regexPattern: String = "^" + pathPrefix + checkedPattern + "$"
  val regex: Regex = regexPattern.r // fails fast if regexPattern is invalid
}
