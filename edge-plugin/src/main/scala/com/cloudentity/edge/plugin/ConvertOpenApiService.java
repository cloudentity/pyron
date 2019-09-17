package com.cloudentity.edge.plugin;

import com.cloudentity.edge.plugin.openapi.*;
import com.cloudentity.tools.vertx.bus.VertxEndpoint;
import com.cloudentity.tools.vertx.tracing.TracingContext;
import io.vertx.core.Future;

public interface ConvertOpenApiService {
  @VertxEndpoint(address = ":plugin.convert-openapi")
  Future<ConvertOpenApiResponse> convertOpenApi(TracingContext ctx, ConvertOpenApiRequest req);
}
