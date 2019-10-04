package com.cloudentity.pyron.plugin.verticle;

import com.cloudentity.pyron.domain.flow.RequestCtx;
import io.vertx.core.json.JsonObject;

public abstract class JavaResponsePluginVerticle extends JavaRequestResponsePluginVerticle  {
  @Override
  public io.vertx.core.Future<RequestCtx> applyJava(RequestCtx requestCtx, JsonObject conf) {
    return io.vertx.core.Future.succeededFuture(requestCtx);
  }
}
