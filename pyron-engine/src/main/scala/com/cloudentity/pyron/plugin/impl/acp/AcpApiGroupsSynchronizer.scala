package com.cloudentity.pyron.plugin.impl.acp

import com.cloudentity.pyron.apigroup.{ApiGroup, ApiGroupConf, ApiGroupsChangeListener, ApiGroupsStore}
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

object AcpApiGroup {
  def apply(g: ApiGroupConf): AcpApiGroup =
    AcpApiGroup(
      g.id.value,
      g.rules
        .filter(_.requestPlugins.toList.exists(_.name == AcpAuthzPlugin.pluginName))
        .map(r => AcpApi(r.rule.criteria.method.name(), g.matchCriteria.basePath.map(_.value).getOrElse("") + r.rule.criteria.path.originalPath))
    )
}

class AcpApiGroupsSynchronizer extends ScalaServiceVerticle with ApiGroupsChangeListener {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(getClass)
  var authorizer: SmartHttpClient = _
  var apiGroups: ApiGroupsStore = _

  implicit val AcpApiEncoder = deriveEncoder[AcpApi]
  implicit val AcpApiGroupEncoder = deriveEncoder[AcpApiGroup]
  implicit val SetAcpApiGroupEncoder: Encoder[SetAcpApiGroup] = setGroup => Map("api_groups" -> setGroup.apiGroups.asJson).asJson

  override def initServiceAsyncS(): Future[Unit] =
    SmartHttp.clientBuilder(vertx, getConfig.getJsonObject("authorizer").getJsonObject("client"))
      .build().toScala()
      .map(authorizer = _)
      .map { _ =>
        createClient(classOf[ApiGroupsStore]).getGroupConfs().toScala.map(apiGroupsChanged(Nil, _))
          .recover {
            case ex: Throwable =>
              log.error(TracingContext.dummy(), "Could not get api groups", ex)
          }
      }

  override def apiGroupsChanged(groups: List[ApiGroup], confs: List[ApiGroupConf]): Unit = {
    authorizer.put("/apis").endWithBody(SetAcpApiGroup(confs.map(AcpApiGroup.apply)).asJson.noSpaces).toScala()
      .onComplete {
        case Success(resp) if resp.getHttp.statusCode == 204 =>
          // ok
        case Success(resp) if resp.getHttp.statusCode != 204 =>
          log.error(TracingContext.dummy(), s"Failed to upload api-groups to ACP, code=${resp.getHttp.statusCode}, body=${resp.getBody}")
        case Failure(ex) =>
          log.error(TracingContext.dummy(), "Failed to upload api-groups to ACP", ex)
      }
  }
}
