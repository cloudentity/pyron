package com.cloudentity.pyron.sample.java;

import com.cloudentity.pyron.domain.flow.RequestCtx;
import com.cloudentity.pyron.domain.http.ApiResponse;
import com.cloudentity.tools.vertx.http.Headers;
import com.cloudentity.pyron.plugin.config.ValidateResponse;
import com.cloudentity.pyron.plugin.verticle.JavaRequestPluginVerticle;
import com.cloudentity.tools.vertx.http.SmartHttp;
import com.cloudentity.tools.vertx.http.SmartHttpClient;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;
import scala.Option;

import java.util.HashMap;

public class AbortPluginVerticle extends JavaRequestPluginVerticle {
  SmartHttpClient client;

  @Override
  public Future initServiceAsync() {
    JsonObject clientConfig = getConfig().getJsonObject("client");

    return SmartHttp.clientBuilder(vertx, clientConfig).build().map(c -> this.client = c);
  }

  @Override
  public String name() {
    return "sample-abort";
  }

  @Override
  public Future<RequestCtx> applyJava(RequestCtx requestCtx, JsonObject conf) {
    String path = getConfig().getString("path");
    String header = conf.getString("header");
    Option<String> valueOpt = requestCtx.request().headers().get(header);

    if (valueOpt.isDefined()) {
      return client.get(path).putHeader(header, valueOpt.get()).end().map((HttpClientResponse response) -> {
        if (response.statusCode() == 200) {
          return requestCtx;
        } else {
          ApiResponse apiResponse = ApiResponse.apply(403, Buffer.buffer(), Headers.of(new HashMap()), Option.apply(null));
          return requestCtx.abort(apiResponse);
        }
      });
    } else {
      ApiResponse apiResponse = ApiResponse.apply(403, Buffer.buffer(), Headers.of(new HashMap()), Option.apply(null));
      return Future.succeededFuture(requestCtx.abort(apiResponse));
    }

  }

  @Override
  public ValidateResponse validate(JsonObject conf) {
    if (conf.getString("header", "").isEmpty()) {
      return ValidateResponse.failure("'header' must not be empty");
    } else return ValidateResponse.ok();
  }
}
