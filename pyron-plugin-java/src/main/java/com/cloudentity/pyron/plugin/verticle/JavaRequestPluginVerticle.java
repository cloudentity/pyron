package com.cloudentity.pyron.plugin.verticle;

import com.cloudentity.pyron.domain.flow.ResponseCtx;
import io.vertx.core.json.JsonObject;

public abstract class JavaRequestPluginVerticle extends JavaRequestResponsePluginVerticle  {
  @Override
  public io.vertx.core.Future<ResponseCtx> applyJava(ResponseCtx responseCtx, JsonObject conf) {
    return io.vertx.core.Future.succeededFuture(responseCtx);
  }
}
