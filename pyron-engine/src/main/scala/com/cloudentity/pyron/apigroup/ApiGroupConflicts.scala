package com.cloudentity.pyron.apigroup

import com.cloudentity.pyron.domain.flow.{BasePath, DomainPattern, GroupMatchCriteria}

object ApiGroupConflicts {

  case class Conflict(pathA: List[String], pathB: List[String])

  def findConflicts(validGroups: List[ValidResult[ApiGroupConfUnresolved]]): List[Either[Conflict, ValidResult[ApiGroupConfUnresolved]]] =
    validGroups.map { validGroup =>
      val otherConflictedGroupOpt =
        validGroups.find { otherGroup =>
          validGroup.path != otherGroup.path && isConflicted(validGroup.value.matchCriteria, otherGroup.value.matchCriteria)
        }

      otherConflictedGroupOpt match {
        case Some(otherConflictedGroup) => Left(Conflict(validGroup.path, otherConflictedGroup.path))
        case None                       => Right(validGroup)
      }
    }

  def isConflicted(a: GroupMatchCriteria, b: GroupMatchCriteria): Boolean =
    isBasePathConflicted(a.basePathResolved, b.basePathResolved) && isDomainsConflicted(a.domains, b.domains)

  def isBasePathConflicted(a: BasePath, b: BasePath) =
    a.value.startsWith(b.value) || b.value.startsWith(a.value)

  def isDomainsConflicted(asOpt: Option[List[DomainPattern]], bsOpt: Option[List[DomainPattern]]): Boolean =
    (asOpt, bsOpt) match {
      case (Some(as), Some(bs)) => as.exists(a => bs.exists(b => isDomainOverlapping(a, b)))
      case (None, _)            => true
      case (_, None)            => true
    }

  def isDomainOverlapping(a: DomainPattern, b: DomainPattern): Boolean =
    a.regex.findFirstMatchIn(b.value).isDefined || b.regex.findFirstMatchIn(a.value).isDefined
}
