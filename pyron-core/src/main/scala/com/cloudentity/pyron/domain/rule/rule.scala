package com.cloudentity.pyron.domain.rule

import com.cloudentity.pyron.domain.flow._
import com.cloudentity.pyron.domain.http.CallOpts

case class RequestPluginsConf(pre: List[ApiGroupPluginConf], endpoint: List[ApiGroupPluginConf], post: List[ApiGroupPluginConf]) extends PluginsConf
case class ResponsePluginsConf(pre: List[ApiGroupPluginConf], endpoint: List[ApiGroupPluginConf], post: List[ApiGroupPluginConf]) extends PluginsConf

sealed trait BodyHandling
  case object BufferBody extends BodyHandling
  case object StreamBody extends BodyHandling
  case object DropBody extends BodyHandling

case class Kilobytes(value: Int) {
  val bytes: Long = value.toLong * 1024
}

case class RuleConf(
  endpointName: Option[String],
  criteria: EndpointMatchCriteria,
  target: TargetServiceRule,
  dropPathPrefix: Boolean,
  rewriteMethod: Option[RewriteMethod],
  rewritePath: Option[RewritePath],
  reroute: Option[Boolean],
  copyQueryOnRewrite: Option[Boolean],
  preserveHostHeader: Option[Boolean],
  tags: List[String],
  requestBody: Option[BodyHandling],
  requestBodyMaxSize: Option[Kilobytes],
  call: Option[CallOpts],
  ext: ExtRuleConf
)
case class RuleConfWithPlugins(rule: RuleConf, requestPlugins: RequestPluginsConf, responsePlugins: ResponsePluginsConf)

case class ExtRuleConf(openapi: Option[OpenApiRuleConf])
case class OpenApiRuleConf(operationId: Option[String])