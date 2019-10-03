package com.cloudentity.pyron.apigroup

import com.cloudentity.pyron.domain.flow.GroupMatchCriteria
import com.cloudentity.pyron.domain.rule.RuleConfWithPlugins
import com.cloudentity.pyron.rule.Rule

case class ApiGroup(matchCriteria: GroupMatchCriteria, rules: List[Rule])
case class ApiGroupConf(matchCriteria: GroupMatchCriteria, rules: List[RuleConfWithPlugins])