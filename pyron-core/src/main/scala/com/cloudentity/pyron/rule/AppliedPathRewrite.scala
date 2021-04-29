package com.cloudentity.pyron.rule

import com.cloudentity.pyron.domain.flow.PathParams

case class AppliedPathRewrite(path: String,
                              targetPath: String,
                              pathParams: PathParams,
                              from: PreparedPathRewrite) {
  def pathPrefix: String = from.pathPrefix

  def matchedPattern: String = from.matchPattern

  def rewritePattern: String = from.rewritePattern

  def getPathParam(paramName: String): Option[String] = pathParams.value.get(paramName)
}
