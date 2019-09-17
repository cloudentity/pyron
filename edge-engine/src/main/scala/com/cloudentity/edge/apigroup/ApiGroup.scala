package com.cloudentity.edge.apigroup

import com.cloudentity.edge.domain.flow.GroupMatchCriteria
import com.cloudentity.edge.domain.rule.RuleConfWithPlugins
import com.cloudentity.edge.rule.Rule

case class ApiGroup(matchCriteria: GroupMatchCriteria, rules: List[Rule])
case class ApiGroupConf(matchCriteria: GroupMatchCriteria, rules: List[RuleConfWithPlugins])