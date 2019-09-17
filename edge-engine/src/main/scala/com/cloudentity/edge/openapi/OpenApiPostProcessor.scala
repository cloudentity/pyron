package com.cloudentity.edge.openapi

import com.cloudentity.tools.vertx.bus.VertxEndpoint
import io.swagger.models.Swagger
import io.vertx.core.Future

trait OpenApiPostProcessor {
  @VertxEndpoint
  def postProcess(openapi: Swagger): Future[Swagger]
}
