package com.cloudentity.pyron.plugin

import com.cloudentity.pyron.plugin.config._
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import io.vertx.core.Future

trait ValidatePluginService {
  @VertxEndpoint(address = ":plugin.validate")
  def validateConfig(req: ValidateRequest): Future[ValidateResponse]
}
