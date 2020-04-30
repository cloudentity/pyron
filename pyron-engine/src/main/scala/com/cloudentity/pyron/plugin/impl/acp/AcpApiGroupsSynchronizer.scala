package com.cloudentity.pyron.plugin.impl.acp

import com.cloudentity.pyron.apigroup.{ApiGroup, ApiGroupConf, ApiGroupsChangeListener, ApiGroupsStore}
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import com.cloudentity.tools.vertx.http.builder.SmartHttpResponse
import com.cloudentity.tools.vertx.http.{SmartHttp, SmartHttpClient}
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.syntax._

import scala.concurrent.Future
import scala.util.{Failure, Success}

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

trait SyncRetry {
  @VertxEndpoint def uploadWithRetry(newestGroups: Option[List[ApiGroupConf]])
}

class AcpApiGroupsSynchronizer extends ScalaServiceVerticle with ApiGroupsChangeListener with SyncRetry {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(getClass)
  var authorizer: SmartHttpClient = _

  var groupsToRetry: Option[List[ApiGroupConf]] = None
  lazy val self = createClient(classOf[SyncRetry])

  implicit val AcpApiEncoder = deriveEncoder[AcpApi]
  implicit val AcpApiGroupEncoder = deriveEncoder[AcpApiGroup]
  implicit val SetAcpApiGroupEncoder: Encoder[SetAcpApiGroup] = setGroup => Map("api_groups" -> setGroup.apiGroups.asJson).asJson

  override def initServiceAsyncS(): Future[Unit] =
    SmartHttp.clientBuilder(vertx, getConfig.getJsonObject("authorizer").getJsonObject("client"))
      .build().toScala()
      .map(authorizer = _)
      .map { _ =>
        val store = createClient(classOf[ApiGroupsStore])
        store.getGroupConfs().toScala.map(apiGroupsChanged(Nil, _))
          .recover {
            case ex: Throwable =>
              log.error(TracingContext.dummy(), "Could not get api groups", ex)
          }
      }

  override def apiGroupsChanged(groups: List[ApiGroup], confs: List[ApiGroupConf]): Unit = {
    groupsToRetry = None
    uploadWithRetry(Some(confs))
  }

  override def uploadWithRetry(newestGroups: Option[List[ApiGroupConf]]): Unit =
    newestGroups.orElse(groupsToRetry) match {
      case Some(groups) =>
        authorizer.put("/apis").endWithBody(SetAcpApiGroup.fromApiGroupConfs(groups).asJson.noSpaces).toScala()
          .onComplete {
            case Success(resp) if resp.getHttp.statusCode == 204 =>
              // ok
              log.debug(TracingContext.dummy(), "Successfully uploaded api-groups to ACP")
            case Success(resp) if resp.getHttp.statusCode != 204 =>
              log.error(TracingContext.dummy(), s"Failed to upload api-groups to ACP, retrying, code=${resp.getHttp.statusCode}, body=${resp.getBody}")

              groupsToRetry = Some(groups)
              vertx.setTimer(3000, _ => self.uploadWithRetry(None))
            case Failure(ex) =>
              log.error(TracingContext.dummy(), "Failed to upload api-groups to ACP, retrying", ex)

              groupsToRetry = Some(groups)
              vertx.setTimer(3000, _ => self.uploadWithRetry(None))
          }
    }
}
