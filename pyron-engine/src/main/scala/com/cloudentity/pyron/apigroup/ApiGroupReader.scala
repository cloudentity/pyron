package com.cloudentity.pyron.apigroup

import io.circe.{CursorOp, Decoder, DecodingFailure, Json}
import com.cloudentity.pyron.domain.flow.{BasePath, GroupMatchCriteria, PluginName}
import io.circe.generic.semiauto.deriveDecoder
import scalaz._, Scalaz._

case class ApiGroupLevelRaw(_rules: Option[Json], _group: Option[GroupMatchCriteria], _plugins: Option[ApiGroupLevelPlugins])
case class ApiGroupLevelPlugins(plugins: List[ApiGroupPlugin])

case class PluginId(value: String) extends AnyVal
case class ApiGroupPlugin(plugin: PluginName, id: PluginId)

case class ApiGroupLevel(rules: Option[Json], group: Option[GroupMatchCriteria], plugins: List[ApiGroupPlugin], subs: List[ReadResult[ApiGroupLevel]])
case class ApiGroupConfUnresolved(matchCriteria: GroupMatchCriteria, rules: Json, plugins: List[ApiGroupPlugin])

object ApiGroupReader {

  import com.cloudentity.pyron.domain.Codecs._

  implicit val PluginIdDecoder: Decoder[PluginId] = IdDec(PluginId.apply)
  implicit val ApiGroupPluginDecoder: Decoder[ApiGroupPlugin] = deriveDecoder
  implicit val ApiGroupPluginsDecoder: Decoder[ApiGroupLevelPlugins] =
    Decoder.decodeJson
      .emap(_.asArray.toRight("JSON array required"))
      .map(plugins => plugins.toList.flatMap(_.as[ApiGroupPlugin].toOption)).map(x => ApiGroupLevelPlugins.apply(x))
  implicit val PartialApiGroupDecoder: Decoder[ApiGroupLevelRaw] = deriveDecoder
  val reservedNames = List("_rules", "_group", "_plugins")

  def readApiGroupLevels(jsonString: String): ReadResult[ApiGroupLevel] = {
    def isGroupName(name: String) = !reservedNames.contains(name)
    def decodeLevel(json: Json): Either[DecodingFailure, (ApiGroupLevelRaw, Map[String, Json])] = {
      json.as[ApiGroupLevelRaw].map { partial =>
        val subgroups = json.asObject.map(_.toMap.filterKeys(isGroupName))
        (partial, subgroups.getOrElse(Map()))
      }
    }

    def rec(path: List[String])(json: Json): ReadResult[ApiGroupLevel] = {
      val result: Either[String, ApiGroupLevel] =
        decodeLevel(json).map { case (partial, subLevels) =>
          val decodeSubLevelsResult: List[ReadResult[ApiGroupLevel]] =
            subLevels.toList.sortBy(_._1)
              .map { case (name, subLevel) => rec(name :: path)(subLevel) }

          ApiGroupLevel(partial._rules, partial._group, partial._plugins.map(_.plugins).getOrElse(Nil), decodeSubLevelsResult)
        }.left.map(err => s"Invalid group level at '${path.reverse.mkString(".")}${CursorOp.opsToPath(err.history)}'")

      result.fold(InvalidResult(path, _), ValidResult(path, _))
    }

    io.circe.parser.parse(jsonString) match {
      case Right(json) => rec(Nil)(json)
      case Left(ex)    => InvalidResult(Nil, ex.getMessage)
    }
  }

  def buildApiGroupConfsUnresolved(root: ApiGroupLevel): List[ReadResult[ApiGroupConfUnresolved]] = {
    def rec(levelPath: List[String], level: ApiGroupLevel): List[ReadResult[ApiGroupConfUnresolved]] = {
      val nonEmptySubsOpt = level.subs.headOption.map(_ => level.subs)

      (level.rules, nonEmptySubsOpt) match {
        case (Some(rules), Some(subs)) =>
          List(InvalidResult(levelPath, "leaf node with rules can't have sub-levels"))

        case (Some(rules), None) =>
          List(ValidResult(levelPath, ApiGroupConfUnresolved(level.group.getOrElse(GroupMatchCriteria.empty), rules, level.plugins)))

        case (None, None) =>
          Nil

        case (None, Some(subLevels)) =>
          val subGroups: List[ReadResult[ApiGroupConfUnresolved]] =
            subLevels.flatMap {
              case ValidResult(path, sub)   => rec(path, sub)
              case InvalidResult(path, msg) => List(InvalidResult[ApiGroupConfUnresolved](path, msg))
            }

          val validSubGroups: List[ValidResult[ApiGroupConfUnresolved]] =
            subGroups.flatMap(_.asValid())

          val invalidSubGroups: List[ReadResult[ApiGroupConfUnresolved]] =
            subGroups.flatMap(_.asInvalid())

          compactRulesInSubGroups(levelPath, validSubGroups)
            .map {
              case ValidResult(subPath, validSub) => mergeSubGroupWithParent(level, subPath, validSub)
              case InvalidResult(path, msg)       => InvalidResult[ApiGroupConfUnresolved](path, msg)
            } ++ invalidSubGroups
      }
    }

    rec(Nil, root)
  }

  /**
    * Selects all sub-groups with empty group criteria match and replaces them with single API group with all their rules.
    * Other sub-groups are intact.
    */
  private def compactRulesInSubGroups(levelPath: List[String], validSubGroups: List[ValidResult[ApiGroupConfUnresolved]]): List[ReadResult[ApiGroupConfUnresolved]] = {
    val (subGroupsWithoutMatchCriteria, subGroupsWithMatchCriteria) =
      validSubGroups.partition(_.value.matchCriteria == GroupMatchCriteria.empty)

    val rulesFromSubGroupsWithoutMatchCriteria: List[Json] =
      subGroupsWithoutMatchCriteria.map(_.value.rules)

    val mergedSubGroupsWithoutMatchCriteria: List[ReadResult[ApiGroupConfUnresolved]] =
      if (rulesFromSubGroupsWithoutMatchCriteria.nonEmpty)
        mergeRules(rulesFromSubGroupsWithoutMatchCriteria) match {
          case Right(mergedRules) =>
            val plugins: List[ApiGroupPlugin] = subGroupsWithoutMatchCriteria.flatMap(_.value.plugins)
            List(ValidResult(levelPath, ApiGroupConfUnresolved(GroupMatchCriteria.empty, mergedRules, plugins)))
          case Left(err)          => List(InvalidResult[ApiGroupConfUnresolved](levelPath, err))
        }
      else Nil

    mergedSubGroupsWithoutMatchCriteria ::: subGroupsWithMatchCriteria
  }

  private def mergeSubGroupWithParent(level: ApiGroupLevel, subPath: List[String], validSub: ApiGroupConfUnresolved): ReadResult[ApiGroupConfUnresolved] = {
    val parentDomainsOpt = level.group.flatMap(_.domains)
    val parentBasePath = level.group.flatMap(_.basePath)
    val domainsOpt = validSub.matchCriteria.domains.orElse(parentDomainsOpt)

    (validSub.matchCriteria.domains, parentDomainsOpt) match {
      case (Some(levelDomains), Some(parentDomains)) =>
        InvalidResult[ApiGroupConfUnresolved](subPath, "leaf node with domains set can't inherit them from parent")

      case (_, _) =>
        val criteria =
          GroupMatchCriteria(makeBasePath(parentBasePath, validSub.matchCriteria.basePath), domainsOpt)
        ValidResult(subPath, ApiGroupConfUnresolved(criteria, validSub.rules, mergeApiGroupPlugins(level.plugins, validSub.plugins)))
    }
  }

  private def mergeApiGroupPlugins(parent: List[ApiGroupPlugin], plugins: List[ApiGroupPlugin]): List[ApiGroupPlugin] =
    (parent.map(p => p.plugin -> p).toMap ++ plugins.map(p => p.plugin -> p).toMap).values.toList

  /**
    * Merging list of JsonObjects or JsonArrays.
    * JsonObject is mapped to list of its value.
    * Then all Json values are flattened.
    *
    * If the list contains something else than object or array then Left(error) is returned.
    */
  private def mergeRules(xs: List[Json]): Either[String, Json] =
    xs.map { json =>
      json.asObject.map(_.toMap.values.toList)
        .orElse(json.asArray.map(_.toList))
        .toRight("rules attribute must be JSON array or object")
    }.sequenceU.map(_.flatten).map(Json.fromValues)

  /**
    * Prepends level base path to sub-level base path.
    */
  private def makeBasePath(levelBasePathOpt: Option[BasePath], subBasePath: Option[BasePath]): Option[BasePath] =
    (levelBasePathOpt, subBasePath) match {
      case (Some(parentBasePath), Some(levelBasePath)) =>
        Some(BasePath(parentBasePath.value + levelBasePath.value))
      case (None, Some(levelBasePath)) =>
        Some(levelBasePath)
      case (Some(parentBasePath), None) =>
        Some(parentBasePath)
      case (None, None) =>
        None
    }
}
