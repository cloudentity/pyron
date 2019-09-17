package com.cloudentity.edge.rule

import com.cloudentity.edge.domain.flow.GroupMatchCriteria

object ApiGroupMatcher {
  def makeMatch(hostOpt: Option[String], path: String, criteria: GroupMatchCriteria): Boolean = {
    val domainMatches =
      criteria.domains match {
        case Some(domains) =>
          hostOpt match {
            case Some(host) => domains.find(_.regex.findFirstMatchIn(host).isDefined).isDefined
            case None       => false
          }
        case None => true
      }

    domainMatches && path.startsWith(criteria.basePathResolved.value)
  }

}
