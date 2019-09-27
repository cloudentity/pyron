package com.cloudentity.edge.rule

import com.cloudentity.edge.domain.flow.GroupMatchCriteria

object ApiGroupMatcher {
  def makeMatch(hostOpt: Option[String], path: String, criteria: GroupMatchCriteria): Boolean = {
    val domainMatches =
      criteria.domains match {
        case Some(domains) =>
          hostOpt match {
            case Some(host) =>
              val hostWithoutPort = host.takeWhile(_ != ':')
              domains.find(_.regex.findFirstMatchIn(hostWithoutPort).isDefined).isDefined
            case None =>
              false
          }
        case None => true
      }

    domainMatches && path.startsWith(criteria.basePathResolved.value)
  }

}
