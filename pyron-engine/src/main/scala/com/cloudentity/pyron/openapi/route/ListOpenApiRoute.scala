package com.cloudentity.pyron.openapi.route

import com.cloudentity.pyron.apigroup.{ApiGroup, ApiGroupConf, ApiGroupsChangeListener, ApiGroupsStore}
import com.cloudentity.pyron.domain.Codecs.{AnyValDecoder, AnyValEncoder}
import com.cloudentity.pyron.domain.flow.ServiceClientName
import com.cloudentity.pyron.domain.Codecs._
import com.cloudentity.pyron.openapi.Codecs._
import com.cloudentity.pyron.domain.openapi.ServiceId
import com.cloudentity.pyron.openapi._
import com.cloudentity.pyron.openapi.route.ListOpenApiRoute._
import com.cloudentity.pyron.util.ConfigDecoder
import com.cloudentity.tools.vertx.scala.Operation
import com.cloudentity.tools.vertx.server.api.errors.ApiError
import com.cloudentity.tools.vertx.server.api.routes.ScalaRouteVerticle
import com.cloudentity.tools.vertx.server.api.routes.utils.CirceRouteOperations
import com.cloudentity.tools.vertx.server.http.HttpStatus
import com.cloudentity.tools.vertx.tracing.LoggingWithTracing
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.models.Swagger
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.ext.web.RoutingContext
import scalaz.{-\/, \/-}

import scala.collection.JavaConverters._
import scala.concurrent.Future

object ListOpenApiRoute {
  val verticleId = "listOpenApi"

  case class ListOpenApiRouteConf(location: Option[ListOpenApiLocation], excludedServices: Option[List[ServiceClientName]])
  case class ListOpenApiLocation(host: Option[Host], port: Option[Int], ssl: Option[Boolean], basePath: Option[BasePath])

  case class Url(value: String) extends AnyVal
  case class Host(value: String) extends AnyVal
  case class BasePath(value: String) extends AnyVal

  object ServiceMetadata {
    def withUrl(url: Url) = ServiceMetadata(Some(url), true)
    def notAvailable() = ServiceMetadata(None, false)
  }
  case class ServiceMetadata(url: Option[Url], available: Boolean)
  case class ListOpenApiResponse(services: Map[ServiceId, ServiceMetadata])

  implicit lazy val urlEncoder: Encoder[Url] = AnyValEncoder(_.value)
  implicit lazy val urlDecoder: Decoder[Url] = AnyValDecoder(Url)
  implicit lazy val hostDecoder: Decoder[Host] = AnyValDecoder(Host)
  implicit lazy val basePathDecoder: Decoder[BasePath] = AnyValDecoder(BasePath)
  implicit lazy val serviceMetadataEncoder = deriveEncoder[ServiceMetadata]
  implicit lazy val serviceMetadataDecoder = deriveDecoder[ServiceMetadata]

  implicit lazy val listOpenApiResponseEncoder = deriveEncoder[ListOpenApiResponse]
  implicit lazy val openApiInfoResponseDecoder = deriveDecoder[ListOpenApiResponse]
  implicit lazy val listOpenApiLocationDecoder = deriveDecoder[ListOpenApiLocation]
  implicit lazy val listOpenApiRouteConfDecoder = deriveDecoder[ListOpenApiRouteConf]
}

class ListOpenApiRoute extends ScalaRouteVerticle with CirceRouteOperations with ConfigDecoder with ApiGroupsChangeListener {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  var apiGroupConfs: List[ApiGroupConf] = List()
  var cfg: ListOpenApiRouteConf = _

  protected override def vertxServiceAddressPrefixS: Option[String] = Some(ListOpenApiRoute.verticleId)
  override def configPath(): String = ListOpenApiRoute.verticleId

  lazy val openApiService = createClient(classOf[OpenApiService], new DeliveryOptions().setSendTimeout(30000))

  override def initServiceAsyncS(): Future[Unit] = {
    cfg = decodeConfigOptUnsafe(ListOpenApiRouteConf(None, None))

    lazy val apiGroupsStore = createClient(classOf[ApiGroupsStore])
    for {
      confs     <- apiGroupsStore.getGroupConfs().toScala()
      groups    <- apiGroupsStore.getGroups().toScala()
    } yield reloadApiGroups(groups, confs)
  }

  override def apiGroupsChanged(groups: List[ApiGroup], confs: List[ApiGroupConf]): Unit =
    reloadApiGroups(groups, confs)

  def reloadApiGroups(groups: List[ApiGroup], confs: List[ApiGroupConf]): Unit =
    apiGroupConfs = confs

  override protected def handle(ctx: RoutingContext): Unit = {
    val tag = ctx.queryParam("tag").asScala.headOption

    val futures: Set[Future[(ServiceId, OpenApiService.OpenApiServiceError \/ Swagger)]] =
      servicesWithAtLeastOneRule.map { service =>
        openApiService.build(ctx.tracing, service, tag).toScala().map(openapi => (service, openapi))
      }

    val program: Operation[ApiError, ListOpenApiResponse] =
      Future.sequence(futures).toOperation[ApiError]
        .map { services =>
          services.map {
            case (serviceId, \/-(_)) =>
              (serviceId -> ServiceMetadata.withUrl(openApiUrl(serviceId)))
            case (serviceId, -\/(ex)) =>
              log.debug(ctx.tracing, s"Failed to generate openapi for service: ${serviceId}", ex)
              (serviceId -> ServiceMetadata.notAvailable())
          }.toMap
        }.map(ListOpenApiResponse.apply)

    handleCompleteS(ctx, HttpStatus.OK)(program.run)
  }

  private def openApiUrl(serviceId: ServiceId): Url = {
    val ssl = cfg.location.flatMap(_.ssl).getOrElse(false)
    val host = cfg.location.flatMap(_.host.map(_.value)).getOrElse("localhost")
    val port = cfg.location.flatMap(_.port).getOrElse(80)
    val basePath = cfg.location.flatMap(_.basePath.map(_.value)).getOrElse("/api")

    val portS = if (port != 80 && port != 443) ":" + port else ""
    val sslS = if(ssl) "s" else ""

    Url(s"http${sslS}://${host}${portS}${basePath}/${serviceId.toString}")
  }

  private def servicesWithAtLeastOneRule(): Set[ServiceId] = {
    for {
        group <- apiGroupConfs
        rule  <- group.rules
      } yield OpenApiRuleBuilder.from(rule, group.matchCriteria)
    }.flatten
      .groupBy(_.serviceId)
      .filter(_._2.nonEmpty)
      .keySet
      .filterNot(isExcluded(_))

  private def isExcluded(serviceId: ServiceId): Boolean = {
    val excludedServices = cfg.excludedServices.getOrElse(List()).map(_.value)
    excludedServices.contains(serviceId.toString)
  }

}
