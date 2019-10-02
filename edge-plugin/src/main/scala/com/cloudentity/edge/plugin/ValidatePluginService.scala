package com.cloudentity.edge.plugin

import com.cloudentity.edge.plugin.config._
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import io.vertx.core.Future

trait ValidatePluginService {
  @VertxEndpoint(address = ":plugin.validate")
  def validateConfig(req: ValidateRequest): Future[ValidateResponse]
}
