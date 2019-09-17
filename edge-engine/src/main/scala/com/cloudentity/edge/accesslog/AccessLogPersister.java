package com.cloudentity.edge.accesslog;

import com.cloudentity.tools.vertx.bus.VertxEndpoint;
import com.cloudentity.tools.vertx.tracing.TracingContext;

public interface AccessLogPersister {
  @VertxEndpoint
  void persist(TracingContext ctx, AccessLog log);
}
