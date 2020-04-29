package com.cloudentity.pyron.openapi

import com.cloudentity.tools.vertx.bus.VertxEndpoint
import io.swagger.models.Swagger
import io.vertx.core.Future

trait OpenApiPreProcessor {
  @VertxEndpoint
  def preProcess(openapi: Swagger): Future[Swagger]
}
