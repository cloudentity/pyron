package com.cloudentity.pyron.apigroup

import com.cloudentity.pyron.domain.flow.GroupMatchCriteria
import com.cloudentity.pyron.domain.rule.RuleConfWithPlugins
import com.cloudentity.pyron.rule.Rule

case class ApiGroupId(value: String)
case class ApiGroup(id: ApiGroupId, matchCriteria: GroupMatchCriteria, rules: List[Rule])
case class ApiGroupConf(id: ApiGroupId, matchCriteria: GroupMatchCriteria, rules: List[RuleConfWithPlugins])

object ApiGroup {
  val propertiesKey = "_internal.apiGroup"
}