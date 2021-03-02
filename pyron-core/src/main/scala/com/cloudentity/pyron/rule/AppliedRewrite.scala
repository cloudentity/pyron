package com.cloudentity.pyron.rule

import com.cloudentity.pyron.domain.flow.PathParams

case class AppliedRewrite(path: String,
                          targetPath: String,
                          pathParams: PathParams,
                          from: PreparedRewrite) {
  def pathPrefix: String = from.pathPrefix

  def checkedPattern: String = from.checkedPattern

  def rewritePattern: String = from.rewritePattern

  def getPathParam(paramName: String): Option[String] = pathParams.value.get(paramName)
}
