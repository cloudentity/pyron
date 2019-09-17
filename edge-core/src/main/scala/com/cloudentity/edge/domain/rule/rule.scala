package com.cloudentity.edge.domain.rule

import com.cloudentity.edge.domain.flow._
import com.cloudentity.edge.domain.http.CallOpts

case class RequestPluginsConf(pre: List[PluginConf], endpoint: List[PluginConf], post: List[PluginConf]) extends PluginsConf
case class ResponsePluginsConf(pre: List[PluginConf], endpoint: List[PluginConf], post: List[PluginConf]) extends PluginsConf

case class RuleConf(endpointName: Option[String], criteria: EndpointMatchCriteria, target: TargetServiceRule, dropPathPrefix: Boolean, rewriteMethod: Option[RewriteMethod], rewritePath: Option[RewritePath], copyQueryOnRewrite: Option[Boolean], preserveHostHeader: Option[Boolean], tags: List[String], call: Option[CallOpts])
case class RuleConfWithPlugins(rule: RuleConf, requestPlugins: RequestPluginsConf, responsePlugins: ResponsePluginsConf)
