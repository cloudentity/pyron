package com.cloudentity.pyron.api

import com.cloudentity.pyron.domain.flow.FlowId
import com.cloudentity.tools.vertx.server.api.tracing.RoutingWithTracingS
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingManager}
import io.vertx.ext.web.RoutingContext

object CorrelationIdHandler {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  def handle(tracing: TracingManager)(ctx: RoutingContext): Unit = {
    val generatedId = java.util.UUID.randomUUID.toString
    val tracingCtx = RoutingWithTracingS.getOrCreate(ctx, tracing)
    log.debug(tracingCtx, s"Generating new flow id within api gateway $generatedId")

    RoutingCtxData.setFlowId(ctx, FlowId(generatedId))
    RoutingCtxData.setCorrelationSignature(ctx, generatedId)

    ctx.next
  }
}
