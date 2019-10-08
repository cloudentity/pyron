package com.cloudentity.pyron.sample.java;

import com.cloudentity.pyron.domain.flow.RequestCtx;
import com.cloudentity.pyron.domain.http.ApiResponse;
import com.cloudentity.pyron.plugin.config.ValidateResponse;
import com.cloudentity.pyron.plugin.verticle.JavaRequestPluginVerticle;
import com.cloudentity.tools.vertx.http.Headers;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import scala.Option;

import java.util.Optional;

/**
 * Verifies that the API client sends an API key in the request header that matches the one configured for that API.
 * If they do match then the request is proxied to the target service. Otherwise, the request is aborted and error response is sent back to the client.
 *
 * Rule configuration:
 * {
 *   "name": "sample-verify-apikey",
 *   "conf": {
 *     "apiKey": "secret-api-key"
 *   }
 * }
 *
 * Verticle configuration:
 * see src/main/resources/modules/plugin/java/verify-apikey.json
 */
public class VerifyApiKeyPluginVerticle extends JavaRequestPluginVerticle {
  @Override
  public String name() {
    return "sample-verify-apikey";
  }

  private ApiResponse unauthorizedResponse;
  private String defaultApiKeyHeader;

  @Override
  public void initService() {
    defaultApiKeyHeader = Optional.of(getConfig().getString("defaultApiKeyHeader")).get();
    unauthorizedResponse =
      ApiResponse.create(
        getConfig().getInteger("invalidKeyStatusCode"),
        Buffer.buffer(),
        Headers.empty()
      );
  }

  @Override
  public Future<RequestCtx> applyJava(RequestCtx requestCtx, JsonObject conf) {
    Option<String> apiKeyValueOpt = requestCtx.request().headers().get(defaultApiKeyHeader);

    if (apiKeyValueOpt.isDefined() && apiKeyValueOpt.get().equals(conf.getString("apiKey"))) {
      // continue request flow
      return Future.succeededFuture(requestCtx);
    } else {
      // abort request and return response to the client
      return Future.succeededFuture(requestCtx.abort(unauthorizedResponse));
    }
  }

  @Override
  public ValidateResponse validate(JsonObject conf) {
    String apiKey = conf.getString("apiKey");
    if (apiKey == null || apiKey.isEmpty()) {
      return ValidateResponse.failure("'apiKey' must be not empty");
    } else {
      return ValidateResponse.ok();
    }
  }
}