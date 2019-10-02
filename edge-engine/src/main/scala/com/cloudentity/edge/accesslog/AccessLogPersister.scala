package com.cloudentity.edge.accesslog

import com.cloudentity.tools.vertx.bus.VertxEndpoint
import com.cloudentity.tools.vertx.tracing.TracingContext

trait AccessLogPersister {
  @VertxEndpoint
  def persist(ctx: TracingContext, log: AccessLog): Unit
}
