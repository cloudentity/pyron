package com.cloudentity.edge.rule

import com.cloudentity.edge.domain.rule.RuleConf
import com.cloudentity.edge.plugin.PluginFunctions.{RequestPlugin, ResponsePlugin}

case class Rule(conf: RuleConf, requestPlugins: List[RequestPlugin], responsePlugins: List[ResponsePlugin])