package com.cloudentity.pyron.plugin.verticle;

import com.cloudentity.pyron.domain.flow.RequestCtx;
import com.cloudentity.pyron.domain.flow.ResponseCtx;
import com.cloudentity.pyron.plugin.config.ValidateResponse;
import com.cloudentity.tools.vertx.scala.Futures;
import io.circe.Decoder;
import io.vertx.core.json.JsonObject;
import scala.concurrent.Future;

public abstract class JavaRequestResponsePluginVerticle extends RequestResponsePluginVerticle<JsonObject>  {
  public Future<RequestCtx> apply(RequestCtx requestCtx, JsonObject conf) {
    return Futures.toScala(applyJava(requestCtx, conf), executionContext());
  }

  public abstract io.vertx.core.Future<RequestCtx> applyJava(RequestCtx requestCtx, JsonObject conf);

  public Future<ResponseCtx> apply(ResponseCtx responseCtx, JsonObject conf) {
    return Futures.toScala(applyJava(responseCtx, conf), executionContext());
  }

  public abstract io.vertx.core.Future<ResponseCtx> applyJava(ResponseCtx responseCtx, JsonObject conf);

  public abstract ValidateResponse validate(JsonObject conf);

  public Decoder<JsonObject> confDecoder() {
    return JsonObjectDecoder();
  }
}
