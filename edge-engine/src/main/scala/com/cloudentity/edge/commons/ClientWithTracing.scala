package com.cloudentity.edge.commons

import com.cloudentity.tools.vertx.http.builder.RequestCtxBuilder
import com.cloudentity.tools.vertx.tracing.TracingContext

trait ClientWithTracing {
  implicit class RequestCtxBuilderWithTracing(builder: RequestCtxBuilder) {
    def withTracing(ctx: TracingContext): RequestCtxBuilder =
      ctx.foldOverContext[RequestCtxBuilder](builder, (e, b) => b.putHeader(e.getKey, e.getValue))
  }
}

object ClientWithTracing extends ClientWithTracing
