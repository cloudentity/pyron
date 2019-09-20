package com.cloudentity.edge.apigroup

import java.util.Optional

import io.circe.parser.decode
import com.cloudentity.edge.config.Conf.AppConf
import com.cloudentity.tools.vertx.bus.{VertxBus, VertxEndpoint}
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.vertx.config.ConfigChange
import io.vertx.core.json.{JsonArray, JsonObject}
import io.vertx.core.{Future => VxFuture}
import org.slf4j.LoggerFactory
import io.circe.generic.auto._
import com.cloudentity.edge.apigroup.ApiGroupReader.ApiGroupConfUnresolved
import com.cloudentity.edge.domain.flow.GroupMatchCriteria
import com.cloudentity.edge.rule.RulesStore
import com.cloudentity.tools.vertx.json.JsonExtractor

import scala.concurrent.Future

trait ApiGroupsStore {
  @VertxEndpoint
  def getGroups(): VxFuture[List[ApiGroup]]

  @VertxEndpoint
  def getGroupConfs(): VxFuture[List[ApiGroupConf]]
}

case class ApiGroupsChanged(groups: List[ApiGroup], confs: List[ApiGroupConf])

object ApiGroupsStoreVerticle {
  val PUBLISH_API_GROUPS_ADDRESS = "com.cloudentity.edge.publish-api-groups"
}

class ApiGroupsStoreVerticle extends ScalaServiceVerticle with ApiGroupsStore {
  val log = LoggerFactory.getLogger(this.getClass)

  private val appConfPath = "app"
  private val apiGroupsConfPath = "apiGroups"

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
      (groups, confs) <- buildApiGroupsOrFallbackToRules(conf, Option(getConfig()))
    } yield (groups, confs)

  override def configPath(): String = "apiGroups"

  private def getAppConf(): Future[AppConf] =
    getConfService.getConf(appConfPath).toScala().map(Option(_).getOrElse(new JsonObject)).flatMap(decodeAppConf)

  private def decodeAppConf(conf: JsonObject): Future[AppConf] = {
    decode[AppConf](conf.toString) match {
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
    JsonExtractor.resolve(root, appConfPath).flatMap[AnyRef](rulesConf => Optional.ofNullable(rulesConf)).orElse(new JsonArray())

  private def extractApiGroupsConfig(root: JsonObject): AnyRef =
    JsonExtractor.resolve(root, apiGroupsConfPath).flatMap[AnyRef](groupsConf => Optional.ofNullable(groupsConf)).orElse(new JsonObject())

  private def publishApiGroups(): Unit =
    VertxBus.publish(vertx.eventBus(), ApiGroupsStoreVerticle.PUBLISH_API_GROUPS_ADDRESS, ApiGroupsChanged(apiGroups, confs))

  private def buildApiGroupsOrFallbackToRules(conf: AppConf, apiGroupsConfOpt: Option[JsonObject]): Future[(List[ApiGroup], List[ApiGroupConf])] = {
    (conf.rules, conf.defaultProxyRules, apiGroupsConfOpt) match {
      case (None, None, None) =>
        Future.failed(new Exception("API Groups and Rules are missing. Configure 'apiGroups' or 'rules'."))
      case (_, _, None) =>
        // fallback for deprecated Rules configuration
        for {
          rules <- rulesStore.getDeprecatedRules().toScala
          confs <- rulesStore.getDeprecatedRuleConfsWithPlugins().toScala
        } yield {
          val emptyMatchCriteria = GroupMatchCriteria(None, None)
          (ApiGroup(emptyMatchCriteria, rules) :: Nil, ApiGroupConf(emptyMatchCriteria, confs) :: Nil)
        }
      case (None, None, Some(apiGroups)) =>
        buildApiGroups(apiGroups)
      case (Some(_), _, Some(_)) | (_, Some(_), Some(_)) =>
        Future.failed(new Exception("Both API Groups and Rules can't be defined. Move Rules to Api Groups."))
    }
  }

  private def buildApiGroups(apiGroupsConf: JsonObject): Future[(List[ApiGroup], List[ApiGroupConf])] =
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
            rulesStore.decodeRules(loggingTag(group), group.value.rules).toScala()
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
              val invalidMsgs = invalidGroups.map(invalidMsg).mkString("[", ", ", "]")
              Future.failed(new Exception("Reading API Groups failed: " + invalidMsgs))
            }
          }
      case x: InvalidResult[_] =>
        Future.failed(new Exception(s"""Reading API Groups failed: ["${invalidMsg(x)}"]"""))
    }

  private def loggingTag(group: ValidResult[ApiGroupConfUnresolved]) = {
    val basePathTag = s"'${group.value.matchCriteria.basePathResolved.value}'"
    val domainsTag = group.value.matchCriteria.domains.map(ds => s", domains=${ds.map(_.value).mkString("[", ",", "]")}").getOrElse("")

    s"apiGroup='${group.path.mkString(".")}', basePath=$basePathTag$domainsTag"
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
