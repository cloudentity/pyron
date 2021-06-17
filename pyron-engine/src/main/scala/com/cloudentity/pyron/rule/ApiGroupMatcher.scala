package com.cloudentity.pyron.rule

import com.cloudentity.pyron.domain.flow.GroupMatchCriteria

object ApiGroupMatcher {

  def makeMatch(hostOpt: Option[String], path: String, criteria: GroupMatchCriteria): Boolean =
    path.startsWith(criteria.basePathResolved.value) && (criteria.domains match {
      case Some(domains) => hostOpt match {
        case Some(host) =>
          val hostWithoutPort = host.takeWhile(_ != ':')
          domains.exists(_.regex.findFirstMatchIn(hostWithoutPort).nonEmpty)
        case None => false
      }
      case None => true
    })
}