package com.cloudentity.pyron.apigroup

import java.util.Optional

import com.cloudentity.pyron.config.Conf.AppConf
import com.cloudentity.tools.vertx.bus.{VertxBus, VertxEndpoint}
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.vertx.config.ConfigChange
import io.vertx.core.json.{JsonArray, JsonObject}
import io.vertx.core.{Future => VxFuture}
import org.slf4j.LoggerFactory
import io.circe.generic.auto._
import com.cloudentity.pyron.apigroup.ApiGroupReader.ApiGroupConfUnresolved
import com.cloudentity.pyron.config.Conf
import com.cloudentity.pyron.rule.RulesStore
import com.cloudentity.tools.vertx.json.JsonExtractor
import io.circe.Json
import io.vertx.core.impl.NoStackTraceThrowable

import scala.concurrent.Future
import scala.util.Try

trait ApiGroupsStore {
  @VertxEndpoint
  def getGroups(): VxFuture[List[ApiGroup]]

  @VertxEndpoint
  def getGroupConfs(): VxFuture[List[ApiGroupConf]]
}

case class ApiGroupsChanged(groups: List[ApiGroup], confs: List[ApiGroupConf])

object ApiGroupsStoreVerticle {
  val PUBLISH_API_GROUPS_ADDRESS = "com.cloudentity.pyron.publish-api-groups"
}

class ApiGroupsStoreVerticle extends ScalaServiceVerticle with ApiGroupsStore {
  val log = LoggerFactory.getLogger(this.getClass)

  var apiGroups: List[ApiGroup] = _
  var confs: List[ApiGroupConf] = _

  var rulesStore: RulesStore = _

  override def initServiceAsyncS(): Future[Unit] = {
    rulesStore = createClient(classOf[RulesStore])
    registerConfChangeConsumer(onApiGroupsChanged)
    loadGroups().map { case (groups, confs) =>
      this.apiGroups = groups
      this.confs = confs
    }
  }

  private def loadGroups(): Future[(List[ApiGroup], List[ApiGroupConf])] =
    for {
      conf            <- getAppConf()
      rules           <- getRulesConf()
      (groups, confs) <- buildApiGroupsOrFallbackToRules(conf, rules, Option(getConfig()))
    } yield (groups, confs)

  override def configPath(): String = "apiGroups"

  private def getAppConf(): Future[AppConf] =
    getConfService.getConf(Conf.appConfPath).toScala().map(Option(_).getOrElse(new JsonObject)).flatMap(decodeAppConf)

  private def getRulesConf(): Future[Option[Json]] =
    getConfService.getGlobalConf().toScala().map(conf => Option(conf.getValue(Conf.rulesConfPath))).flatMap { rulesOpt =>
      rulesOpt match {
        case Some(rules) =>
          io.circe.parser.parse(rules.toString) match {
            case Right(parsedRules) => Future.successful(Some(parsedRules))
            case Left(_)            => Future.failed(new NoStackTraceThrowable("Could not parse 'rules' configuration"))
          }
        case None => Future.successful(None)
      }
    }

  private def decodeAppConf(conf: JsonObject): Future[AppConf] = {
    Try(Conf.decodeUnsafe(conf.toString)).toEither match {
      case Right(appConf) => Future.successful(appConf)
      case Left(err)      => Future.failed(err)
    }
  }

  override def getGroups(): VxFuture[List[ApiGroup]] =
    if (apiGroups != null) VxFuture.succeededFuture(apiGroups)
    else               VxFuture.failedFuture(new Exception("API Groups has not been set"))

  override def getGroupConfs(): VxFuture[List[ApiGroupConf]] =
    if (confs != null) VxFuture.succeededFuture(confs)
    else               VxFuture.failedFuture(new Exception("API Groups has not been set"))

  private def onApiGroupsChanged(change: ConfigChange): Unit = {
    val prevRules = extractRulesConfig(change.getPreviousConfiguration)
    val nextRules = extractRulesConfig(change.getNewConfiguration)

    val prevApiGroups = extractApiGroupsConfig(change.getPreviousConfiguration)
    val nextApiGroups = extractApiGroupsConfig(change.getNewConfiguration)

    if (prevApiGroups != nextApiGroups || prevRules != nextRules) {
      loadGroups().map { case (groups, confs) =>
        this.apiGroups = groups
        this.confs = confs
        publishApiGroups()
      }.failed.foreach { ex =>
        log.error(s"Could not update API Groups configuration. Old=$prevApiGroups, new=$nextApiGroups", ex)
      }

    } else {
      log.debug("API Groups configuration did not change")
    }
  }

  private def extractRulesConfig(root: JsonObject): AnyRef =
    Option(root.getValue(Conf.rulesConfPath)).getOrElse(new JsonArray())

  private def extractApiGroupsConfig(root: JsonObject): AnyRef =
    JsonExtractor.resolve(root, Conf.apiGroupsConfPath).flatMap[AnyRef](groupsConf => Optional.ofNullable(groupsConf)).orElse(new JsonObject())

  private def publishApiGroups(): Unit =
    VertxBus.publish(vertx.eventBus(), ApiGroupsStoreVerticle.PUBLISH_API_GROUPS_ADDRESS, ApiGroupsChanged(apiGroups, confs))

  private def buildApiGroupsOrFallbackToRules(conf: AppConf, rules: Option[Json], apiGroupsConfOpt: Option[JsonObject]): Future[(List[ApiGroup], List[ApiGroupConf])] = {
    val defaultProxyRulesOpt = conf.defaultProxyRules.filter(_ => conf.defaultProxyRulesEnabled.getOrElse(false))

    (rules, apiGroupsConfOpt) match {
      case (None, None) =>
        Future.failed(new Exception("API Groups and Rules are missing. Configure 'apiGroups' or 'rules'."))
      case (Some(rules), None) =>
        // fallback for deprecated Rules configuration
        val rulesValue = Try(new JsonArray(rules.noSpaces)).orElse(Try(new JsonObject(rules.noSpaces))).toOption.getOrElse(null)
        buildApiGroups(new JsonObject().put("_rules", rulesValue), defaultProxyRulesOpt, true)
      case (None, Some(apiGroups)) =>
        buildApiGroups(apiGroups, defaultProxyRulesOpt, false)
      case (Some(_), Some(_)) =>
        Future.failed(new Exception("Both API Groups and Rules can't be defined. Move Rules to Api Groups."))
    }
  }

  private def buildApiGroups(apiGroupsConf: JsonObject, defaultProxyRulesOpt: Option[Json], deprecatedRules: Boolean): Future[(List[ApiGroup], List[ApiGroupConf])] =
    ApiGroupReader.readApiGroupLevels(apiGroupsConf.toString) match {
      case ValidResult(_, groupLevel) =>
        val unresolvedGroupResults: List[ReadResult[ApiGroupConfUnresolved]] =
          ApiGroupReader.buildApiGroupConfsUnresolved(groupLevel)

        val validUnresolvedGroups   = unresolvedGroupResults.flatMap(_.asValid)
        val invalidUnresolvedGroups = unresolvedGroupResults.flatMap(_.asInvalid)

        val conflictedOrOkGroups: List[ReadResult[ApiGroupConfUnresolved]] =
          invalidateConflicted(validUnresolvedGroups)

        val okGroups         = conflictedOrOkGroups.flatMap(_.asValid())
        val conflictedGroups = conflictedOrOkGroups.flatMap(_.asInvalid())

        val resolvedGroupFuts: List[Future[ReadResult[(ApiGroup, ApiGroupConf)]]] =
          okGroups.map { group =>
            rulesStore.decodeRules(loggingTag(group), group.value.rules, defaultProxyRulesOpt).toScala()
              .map { case (rs, rsConfs) =>
                ValidResult(group.path, (ApiGroup(group.value.matchCriteria, rs), ApiGroupConf(group.value.matchCriteria, rsConfs)))
              }
              .recover { case ex: Throwable =>
                InvalidResult[(ApiGroup, ApiGroupConf)](group.path, ex.getMessage)
              }
          }

        Future.sequence(resolvedGroupFuts)
          .flatMap { resolvedGroups =>
            val validResolvedGroups   = resolvedGroups.flatMap(_.asValid)
            val invalidResolvedGroups = resolvedGroups.flatMap(_.asInvalid)

            val invalidGroups = invalidResolvedGroups ::: invalidUnresolvedGroups ::: conflictedGroups

            if (invalidGroups.isEmpty) {
              Future.successful(validResolvedGroups.map(_.value._1), validResolvedGroups.map(_.value._2))
            } else {
              val invalidMsgs = invalidGroups.map(invalidMsg).mkString("\n")
              Future.failed(new Exception("\n" + invalidMsgs))
            }
          }
      case x: InvalidResult[_] =>
          Future.failed(new Exception(s"""Reading rules failed: ["${invalidMsg(x)}"]"""))
    }

  private def loggingTag(group: ValidResult[ApiGroupConfUnresolved]) = {
    val groupTagOpt = if (group.path.nonEmpty) Some(s"apiGroup=${group.path.mkString(".")}") else None
    val basePathTagOpt = group.value.matchCriteria.basePath.map("basePath=" + _)
    val domainsTagOpt = group.value.matchCriteria.domains.map(ds => s"domains=${ds.map(_.value).mkString("[", ",", "]")}")

    List(groupTagOpt, basePathTagOpt, domainsTagOpt).flatten.mkString(", ")
  }

  private def invalidateConflicted(validUnresolvedGroups: List[ValidResult[ApiGroupConfUnresolved]]): List[ReadResult[ApiGroupConfUnresolved]] =
      ApiGroupConflicts.findConflicts(validUnresolvedGroups).map {
        case Right(validGroup) =>
          validGroup
        case Left(conflict) =>
          val errMsg = s"conflicted with '${conflict.pathB.reverse.mkString(".")}'"
          InvalidResult[ApiGroupConfUnresolved](conflict.pathA, errMsg)
      }

  private def invalidMsg(invalid: InvalidResult[_]): String =
    if (invalid.path.nonEmpty) s"${invalid.path.mkString(".")}=${invalid.msg}"
    else invalid.msg
}
