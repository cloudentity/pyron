package com.cloudentity.pyron.api

import com.cloudentity.pyron.domain.rule.Kilobytes
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import io.vertx.core.Future
import io.vertx.ext.web.RoutingContext

trait ApiHandler {
  @VertxEndpoint
  def handle(defaultRequestBodyMaxSize: Option[Kilobytes], ctx: RoutingContext): Future[Unit]
}