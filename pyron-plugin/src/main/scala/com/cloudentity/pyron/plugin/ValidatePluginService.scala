package com.cloudentity.pyron.plugin

import com.cloudentity.pyron.plugin.config._
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import io.vertx.core.Future

trait ValidatePluginService {
  @VertxEndpoint
  def validateConfig(req: ValidateRequest): Future[ValidateResponse]

  @VertxEndpoint
  def validateApplyIf(req: ValidateRequest): Future[ValidateResponse]
}
