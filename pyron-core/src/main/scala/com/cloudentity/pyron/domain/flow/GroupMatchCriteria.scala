package com.cloudentity.pyron.domain.flow

import scala.util.matching.Regex

object GroupMatchCriteria {
  val empty: GroupMatchCriteria = GroupMatchCriteria(None, None)
}

case class GroupMatchCriteria(basePath: Option[BasePath], domains: Option[List[DomainPattern]]) {
  lazy val basePathResolved: BasePath = basePath.getOrElse(BasePath(""))
  lazy val domainsResolved: List[DomainPattern] = domains.getOrElse(Nil)
}

case class BasePath(value: String) extends AnyVal

case class DomainPattern(value: String) {
  lazy val regex = new Regex("^" + value.replace("*", "[^\\.]+") + "$")
}

