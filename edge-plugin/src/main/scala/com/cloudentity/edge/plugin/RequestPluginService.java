package com.cloudentity.edge.plugin;

import com.cloudentity.edge.plugin.bus.request.*;
import com.cloudentity.tools.vertx.bus.VertxEndpoint;
import com.cloudentity.tools.vertx.tracing.TracingContext;
import io.vertx.core.Future;

public interface RequestPluginService extends ValidatePluginService, ConvertOpenApiService {
  @VertxEndpoint(address = ":request-plugin.apply")
  Future<ApplyResponse> applyPlugin(TracingContext ctx, ApplyRequest req);
}
