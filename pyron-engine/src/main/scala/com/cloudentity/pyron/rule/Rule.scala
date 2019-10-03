package com.cloudentity.pyron.rule

import com.cloudentity.pyron.domain.rule.RuleConf
import com.cloudentity.pyron.plugin.PluginFunctions.{RequestPlugin, ResponsePlugin}

case class Rule(conf: RuleConf, requestPlugins: List[RequestPlugin], responsePlugins: List[ResponsePlugin])