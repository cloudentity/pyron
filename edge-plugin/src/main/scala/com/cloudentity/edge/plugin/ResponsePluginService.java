package com.cloudentity.edge.plugin;

import com.cloudentity.edge.plugin.bus.response.*;
import com.cloudentity.tools.vertx.bus.VertxEndpoint;
import com.cloudentity.tools.vertx.tracing.TracingContext;
import io.vertx.core.Future;

public interface ResponsePluginService extends ValidatePluginService, ConvertOpenApiService {
  @VertxEndpoint(address = ":response-plugin.apply")
  Future<ApplyResponse> applyPlugin(TracingContext ctx, ApplyRequest req);
}
