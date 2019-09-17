package com.cloudentity.edge.plugin.impl.authn

import com.cloudentity.edge.domain.flow.AuthnCtx
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.vertx.core.Future

trait EntityProvider {
  @VertxEndpoint
  def getEntity(tracingCtx: TracingContext, ctx: AuthnCtx): Future[AuthnCtx]
}
