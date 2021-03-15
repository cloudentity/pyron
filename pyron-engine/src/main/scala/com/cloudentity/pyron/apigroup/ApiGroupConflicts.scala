package com.cloudentity.pyron.apigroup

import com.cloudentity.pyron.domain.flow.{BasePath, DomainPattern, GroupMatchCriteria}

object ApiGroupConflicts {

  case class Conflict(pathA: List[String], pathB: List[String])

  def findConflicts(validGroups: List[ValidResult[ApiGroupConfUnresolved]]): List[Either[Conflict, ValidResult[ApiGroupConfUnresolved]]] = {
    val validAndConflicted = for {
      (validGroup, idx) <- validGroups.zipWithIndex
    } yield (validGroup, validGroups.drop(idx + 1).find { otherGroup =>
      validGroup.path != otherGroup.path && isConflicted(validGroup.value.matchCriteria, otherGroup.value.matchCriteria)
    })

    validAndConflicted.map {
      case (valid, None) => Right(valid)
      case (valid, Some(conflicted)) => Left(Conflict(valid.path, conflicted.path))
    }
  }

  def isConflicted(a: GroupMatchCriteria, b: GroupMatchCriteria): Boolean =
    isBasePathConflicted(a.basePathResolved, b.basePathResolved) && isDomainsConflicted(a.domains, b.domains)

  def isBasePathConflicted(a: BasePath, b: BasePath): Boolean = {
    b.value.startsWith(a.value)
  }

  def isDomainsConflicted(asOpt: Option[List[DomainPattern]], bsOpt: Option[List[DomainPattern]]): Boolean =
    (asOpt, bsOpt) match {
      case (Some(as), Some(bs)) => as.exists(a => bs.exists(b => isDomainOverlapping(a, b)))
      case (None, _)            => true
      case (_, None)            => true
    }

  def isDomainOverlapping(a: DomainPattern, b: DomainPattern): Boolean =
    a.regex.findFirstMatchIn(b.value).isDefined || b.regex.findFirstMatchIn(a.value).isDefined
}
