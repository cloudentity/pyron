package com.cloudentity.edge.plugin

import com.cloudentity.edge.plugin.openapi._
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.vertx.core.Future

trait ConvertOpenApiService {
  @VertxEndpoint(address = ":plugin.convert-openapi")
  def convertOpenApi(ctx: TracingContext, req: ConvertOpenApiRequest): Future[ConvertOpenApiResponse]
}
