package com.cloudentity.edge.plugin;

import com.cloudentity.edge.plugin.config.*;
import com.cloudentity.tools.vertx.bus.VertxEndpoint;
import io.vertx.core.Future;

public interface ValidatePluginService {
  @VertxEndpoint(address = ":plugin.validate")
  Future<ValidateResponse> validateConfig(ValidateRequest req);
}
