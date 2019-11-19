package com.cloudentity.pyron.openapi.route

import com.cloudentity.pyron.domain.flow.{ServiceClientName, TargetHost}
import com.cloudentity.pyron.domain.Codecs._
import com.cloudentity.pyron.domain.openapi.{DiscoverableServiceId, ServiceId, StaticServiceId}
import com.cloudentity.pyron.openapi.OpenApiService._
import com.cloudentity.pyron.openapi._
import com.cloudentity.pyron.openapi.route.GetOpenApiRoute.GetOpenApiRouteConf
import com.cloudentity.pyron.util.ConfigDecoder
import com.cloudentity.tools.vertx.http.Headers
import com.cloudentity.tools.vertx.http.headers.ContentType
import com.cloudentity.tools.vertx.http.headers.ContentType.{ApplicationJson, ApplicationYaml}
import com.cloudentity.tools.vertx.scala.Operation
import com.cloudentity.tools.vertx.server.api.errors.ApiError
import com.cloudentity.tools.vertx.server.api.routes.ScalaRouteVerticle
import com.cloudentity.tools.vertx.server.api.routes.utils.{CirceRouteOperations, VertxEncoder}
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.models.Swagger
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.http.HttpServerRequest
import io.vertx.ext.web.RoutingContext
import scalaz.\/

import scala.collection.JavaConverters._
import scala.util.Try

object GetOpenApiRoute {
  val verticleId = "getOpenApi"

  case class GetOpenApiRouteConf(excludedServices: Option[List[ServiceClientName]])
  implicit lazy val getOpenApiRouteConfDecoder = deriveDecoder[GetOpenApiRouteConf]
}

class GetOpenApiRoute extends ScalaRouteVerticle with CirceRouteOperations with ConfigDecoder {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  protected override def vertxServiceAddressPrefixS: Option[String] = Some(GetOpenApiRoute.verticleId)
  override def configPath(): String = GetOpenApiRoute.verticleId

  lazy val client = createClient(classOf[OpenApiService], new DeliveryOptions().setSendTimeout(30000))
  var cfg: GetOpenApiRouteConf = _

  implicit val noopEncoder = new VertxEncoder[String] {
    override def encode(a: String): String = a
  }

  override def initService(): Unit = {
    cfg = decodeConfigOptUnsafe(GetOpenApiRouteConf(None))
  }

  def getContentType(request: HttpServerRequest): ContentType =
    request.scalaHeaders.contentType.getOrElse(ApplicationJson)

  override protected def handle(ctx: RoutingContext): Unit = {
    val program: Operation[ApiError, (String, Headers)] =
      for {
        contentType         <- getContentType(ctx.request()).right[ApiError].toOperation
        serviceId           <- getServiceId(ctx)
        tag                 = ctx.queryParam("tag").asScala.headOption
        openapi             <- buildOpenApi(ctx.tracing, serviceId, tag)
        output              <- stringify(ctx.tracing, contentType, openapi).toOperation
      } yield (output, Headers().withContentType(contentType))

    handleCompleteWithHeadersS(ctx, OK)(program.run)
  }

  private def buildOpenApi(ctx: TracingContext, serviceId: ServiceId, tag: Option[String]): Operation[ApiError, Swagger] =
    client.build(ctx, serviceId, tag).toOperation.leftMap[ApiError] {
      case NoRulesFound =>
        log.debug(ctx, s"No Rules found for service: ${serviceId}")
        ApiError.`with`(404, "OpenApiNotFound", "")
      case OpenApiNotFound =>
        log.info(ctx, s"Openapi docs not found for service ${serviceId}")
        ApiError.`with`(404, "OpenApiNotFound", "")
      case ClientError(ex) =>
        log.error(ctx,s"OpenApi client error", ex)
        ApiError.`with`(503, "InvalidOpenApi", "")
      case InvalidStatusCode(code) =>
        log.error(ctx, s"Could not read OpenApi docs for service: ${serviceId}, statusCode=${code}")
        ApiError.`with`(503, "InvalidOpenApi", "")
      case EmptyOpenApi =>
        log.debug(ctx, s"Found empty OpenApi docs for service: ${serviceId}")
        ApiError.`with`(503, "InvalidOpenApi", "")
      case OpenApiParsingError(ex) =>
        log.error(ctx, "OpenApi parsing error", ex)
        ApiError.`with`(503, "InvalidOpenApi", "")
    }

  private def getServiceId(ctx: RoutingContext): Operation[ApiError, ServiceId] =
    for {
      name        <- getPathParam(ctx, "serviceName")
      _           <- checkIfServiceIsExcluded(ctx.tracing, name)
      serviceId <- ctx.queryParam("port").asScala.headOption match {
        case Some(portStr) =>
          val sslStrOpt = ctx.queryParam("ssl").asScala.headOption
          Try((portStr.toInt), sslStrOpt.map(_.toBoolean)).toOption
            .map { case (port, ssl) => StaticServiceId(TargetHost(name), port, ssl.getOrElse(false)) }
            .toOperation(ApiError.`with`(400, "InvalidServiceId", "Could not decode service-id"))

        case None =>
          Operation.success[ApiError, ServiceId](DiscoverableServiceId(ServiceClientName(name)))
      }
    } yield (serviceId)

  def checkIfServiceIsExcluded(ctx: TracingContext, serviceName: String): Operation[ApiError, Unit] = {
    val excludedServices = cfg.excludedServices.getOrElse(List()).map(_.value)
    if (excludedServices.contains(serviceName)) {
      log.debug(ctx, s"Service: ${serviceName} is excluded")
      Operation.error(ApiError.`with`(404, "OpenApiNotFound", ""))
    } else {
      Operation.success(())
    }
  }

  def stringify(ctx: TracingContext, contentType: ContentType, swagger: Swagger): ApiError \/ String = {
    def safeParse(parse: Unit => String): ApiError \/ String = {
      \/.fromTryCatchNonFatal { parse.apply(()) }.leftMap { ex =>
        log.error(ctx, "Failed to parse swagger", ex)
        ApiError.`with`(500, "InvalidOpenApi", "Invalid OpenApi")
      }
    }

    contentType match {
      case ApplicationYaml => safeParse(_ => io.swagger.util.Yaml.pretty().writeValueAsString(swagger))
      case _ => safeParse(_ => io.swagger.util.Json.pretty(swagger))
    }
  }

}