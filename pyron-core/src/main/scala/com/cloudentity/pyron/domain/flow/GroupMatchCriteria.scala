package com.cloudentity.pyron.domain.flow

case class GroupMatchCriteria(basePath: Option[BasePath], domains: Option[List[DomainPattern]]) {
  lazy val basePathResolved: BasePath = basePath.getOrElse(BasePath(""))
  lazy val domainsResolved: List[DomainPattern] = domains.getOrElse(Nil)
}

object GroupMatchCriteria {
  val empty: GroupMatchCriteria = GroupMatchCriteria(None, None)
}