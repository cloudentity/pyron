package com.cloudentity.pyron.plugin.impl.acp

import com.cloudentity.pyron.apigroup.{ApiGroup, ApiGroupConf, ApiGroupsChangeListener, ApiGroupsStore}
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import com.cloudentity.tools.vertx.http.{SmartHttp, SmartHttpClient}
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.syntax._
import com.cloudentity.pyron.domain.Codecs._
import AcpApiGroupsSynchronizer.{VerticleConfig, _}
import com.cloudentity.pyron.util.ConfigDecoder
import io.vertx.core.json.JsonObject

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait ApiGroupsSyncRetry {
  @VertxEndpoint def syncWithRetry(newestGroups: Option[List[ApiGroupConf]])
}

object AcpApiGroupsSynchronizer {
  case class SetAcpApiGroup(apiGroups: List[AcpApiGroup])
  case class AcpApiGroup(id: String, apis: List[AcpApi])
  case class AcpApi(method: String, path: String)

  object SetAcpApiGroup {
    def fromApiGroupConfs(confs: List[ApiGroupConf]): SetAcpApiGroup =
      SetAcpApiGroup(confs.map(AcpApiGroup.apply).filter(_.apis.nonEmpty))
  }

  object AcpApiGroup {
    def apply(g: ApiGroupConf): AcpApiGroup =
      AcpApiGroup(
        g.id.value,
        g.rules
          .filter(_.requestPlugins.toList.exists(_.name == AcpAuthzPlugin.pluginName))
          .map(r => AcpApi(r.rule.criteria.method.name(), r.rule.criteria.path.originalPath))
      )
  }

  case class VerticleConfig(authorizerClient: JsonObject, setApisPath: String, retryDelay: Int)

  implicit val AcpApiEncoder = deriveEncoder[AcpApi]
  implicit val AcpApiGroupEncoder = deriveEncoder[AcpApiGroup]
  implicit val VerticleConfigDecoder = deriveDecoder[VerticleConfig]
  implicit val SetAcpApiGroupEncoder: Encoder[SetAcpApiGroup] = setGroup => Map("api_groups" -> setGroup.apiGroups.asJson).asJson
}

class AcpApiGroupsSynchronizer extends ScalaServiceVerticle with ApiGroupsChangeListener with ApiGroupsSyncRetry with ConfigDecoder {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(getClass)
  var authorizer: SmartHttpClient = _
  var verticleConfig: VerticleConfig = _

  var groupsToRetry: Option[List[ApiGroupConf]] = None
  lazy val self = createClient(classOf[ApiGroupsSyncRetry])

  override def initServiceAsyncS(): Future[Unit] = {
    verticleConfig = decodeConfigUnsafe[VerticleConfig]

    SmartHttp.clientBuilder(vertx, verticleConfig.authorizerClient)
      .build().toScala()
      .map(authorizer = _)
      .flatMap(_ => getApiGroups().map(Option.apply).map(syncWithRetry))
  }

  private def getApiGroups(): Future[List[ApiGroupConf]] =
    createClient(classOf[ApiGroupsStore]).getGroupConfs().toScala
      .recoverWith {
        case ex: Throwable =>
          log.error(TracingContext.dummy(), "Could not get api groups", ex)
          Future.failed(ex)
      }

  override def apiGroupsChanged(groups: List[ApiGroup], confs: List[ApiGroupConf]): Unit = {
    groupsToRetry = None
    syncWithRetry(Some(confs))
  }

  override def syncWithRetry(newestGroups: Option[List[ApiGroupConf]]): Unit =
    newestGroups.orElse(groupsToRetry) match {
      case Some(groups) =>
        authorizer.put(verticleConfig.setApisPath).endWithBody(SetAcpApiGroup.fromApiGroupConfs(groups).asJson.noSpaces).toScala()
          .onComplete {
            case Success(resp) if resp.getHttp.statusCode == 204 =>
              // ok
              groupsToRetry = None
              log.debug(TracingContext.dummy(), "Successfully uploaded api-groups to ACP")
            case Success(resp) if resp.getHttp.statusCode != 204 =>
              log.error(TracingContext.dummy(), s"Failed to upload api-groups to ACP, retrying, code=${resp.getHttp.statusCode}, body=${resp.getBody}")

              groupsToRetry = Some(groups)
              vertx.setTimer(verticleConfig.retryDelay, _ => self.syncWithRetry(None))
            case Failure(ex) =>
              log.error(TracingContext.dummy(), "Failed to upload api-groups to ACP, retrying", ex)

              groupsToRetry = Some(groups)
              vertx.setTimer(verticleConfig.retryDelay, _ => self.syncWithRetry(None))
          }
      case None =>
        log.info(TracingContext.dummy(), "Skipping api-groups upload retry, already uploaded newer data")
    }
}
