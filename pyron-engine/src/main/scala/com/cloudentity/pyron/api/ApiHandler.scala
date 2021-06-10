package com.cloudentity.pyron.api

import com.cloudentity.pyron.config.Conf.AppConf
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import io.vertx.core.{Future => VxFuture}
import io.vertx.ext.web.RoutingContext

trait ApiHandler {
  @VertxEndpoint
  def handle(conf: AppConf, ctx: RoutingContext): VxFuture[Unit]
}