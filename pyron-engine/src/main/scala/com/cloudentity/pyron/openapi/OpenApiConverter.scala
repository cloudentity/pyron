package com.cloudentity.pyron.openapi

import com.cloudentity.pyron.domain.openapi.{ConverterConf, OpenApiRule, ServiceId}
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.swagger.models.Swagger
import io.vertx.core.{Future => VxFuture}

trait OpenApiConverter {
  @VertxEndpoint
  def convert(ctx: TracingContext, serviceId: ServiceId, swagger: Swagger, rules: List[OpenApiRule],
              conf: ConverterConf): VxFuture[Swagger]
}
