package com.cloudentity.pyron.plugin

import com.cloudentity.pyron.plugin.openapi._
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.vertx.core.Future

trait ConvertOpenApiService {
  @VertxEndpoint(address = ":plugin.convert-openapi")
  def convertOpenApi(ctx: TracingContext, req: ConvertOpenApiRequest): Future[ConvertOpenApiResponse]
}
