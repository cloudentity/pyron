package com.cloudentity.edge.openapi

import com.cloudentity.edge.domain.openapi.ServiceId
import com.cloudentity.edge.openapi.OpenApiService.OpenApiServiceError
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.swagger.models.Swagger
import io.vertx.core.{Future => VxFuture}
import scalaz.\/

object OpenApiService {
  sealed trait OpenApiServiceError
    case object NoRulesFound extends OpenApiServiceError
    case class OpenApiParsingError(ex: Throwable) extends OpenApiServiceError
    case object EmptyOpenApi extends OpenApiServiceError

  sealed trait TargetServiceError extends OpenApiServiceError
    case object OpenApiNotFound extends TargetServiceError
    case class ClientError(ex: Throwable) extends OpenApiServiceError
    case class InvalidStatusCode(code: Int) extends TargetServiceError
}

trait OpenApiService {
  @VertxEndpoint
  def build(ctx: TracingContext, serviceId: ServiceId, tag: Option[String]): VxFuture[OpenApiServiceError \/ Swagger]
}
