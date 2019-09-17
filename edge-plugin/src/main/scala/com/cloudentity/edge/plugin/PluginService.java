package com.cloudentity.edge.plugin;

import com.cloudentity.tools.vertx.bus.VertxEndpoint;
import io.vertx.core.Future;

public interface PluginService {
  @VertxEndpoint
  Future<Boolean> isReady();
}
