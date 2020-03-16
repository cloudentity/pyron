package com.cloudentity.pyron.plugin.impl.authn

import com.cloudentity.pyron.domain.flow.AuthnCtx
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.vertx.core.Future

trait EntityProvider {
  @VertxEndpoint
  def getEntity(tracingCtx: TracingContext, ctx: AuthnCtx): Future[AuthnCtx]
}

/**
 * Generic entity provider putting all data returned by authn method into authn context.
 */
class MethodCtxEntityProvider extends EntityProvider {
  override def getEntity(tracingCtx: TracingContext, ctx: AuthnCtx): Future[AuthnCtx] =
    Future.succeededFuture(ctx)
}