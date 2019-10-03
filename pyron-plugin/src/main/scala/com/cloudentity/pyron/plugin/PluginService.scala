package com.cloudentity.pyron.plugin

import com.cloudentity.tools.vertx.bus.VertxEndpoint
import io.vertx.core.Future

trait PluginService {
  @VertxEndpoint
  def isReady(): Future[java.lang.Boolean]
}
