## Call external HTTP server from a Java plugin

Before proceeding with this how-to read [Java plugin development guide](plugin-dev-java.md).
The samples used here can be found in `sample-java-plugins` module.

We will use `SmartHttpClient` provided by [vertx-client](https://github.com/Cloudentity/vertx-tools) library to call external HTTP server.
`SmartHttpClient` is a Future-based wrapper of Vertx HTTP client with optional service-discovery, load-balancing and retries.

* [SmartHttpClient configuration](#smart-config)
* [How to initialize client](#init)
* [How to use client](#use)
* [Example](#example)

<a id="smart-config"></a>
### `SmartHttpClient` configuration

```json
{
  "serviceLocation": {
    "host": "example.com",
    "port": 8080,
    "ssl": false,
    "root": "/base-path"
  },
  "http": {}
}
```

| Attribute            | Description                                                                                                                            |
|:---------------------|:---------------------------------------------------------------------------------------------------------------------------------------|
| serviceLocation.host | external server host                                                                                                                   |
| serviceLocation.port | external server port                                                                                                                   |
| serviceLocation.ssl  | external server SSL flag                                                                                                               |
| serviceLocation.root | external server base path                                                                                                              |
| http                 | [HttpClientOptions configuration](https://vertx.io/docs/apidocs/io/vertx/core/http/HttpClientOptions.html) of underlying Vertx client  |

<a id="init"></a>
### How to initialize client

```java
SmartHttpClient client;

public Future initServiceAsync() {
  // assuming that smart-client configuration is in `client` attribute of verticle config
  JsonObject clientConfig = getConfig().getJsonObject("client");

  return SmartHttp.clientBuilder(vertx, clientConfig).build().map(c -> client = c);
}
```

<a id="use"></a>
### How to use client

`client.METHOD()` (e.g. `client.get()`) returns a request builder. Calling `.end` or `.endWithBody` on it triggers the HTTP call.

Use `.end` if you don't care about response body:

```java
SmartHttpClient client;

client.get("/path")
  .end().map(response -> {
    if (response.statusCode() == 200) {
      return requestCtx;
    } else {
      return requestCtx.abort(ApiResponse(403, Buffer.buffer(), Headers()));
    }
  });
```

Otherwise use `.endWithBody`:

```java
SmartHttpClient client;

client.get("/path")
  .end().map(response -> {
    if (response.getHttp().statusCode() == 200) {
      return requestCtx;
    } else {
      return requestCtx.abort(ApiResponse(403, response.getBody(), Headers()));
    }
  });
```

<a id="example"></a>
### Example

As an example, we will implement a request plugin that calls an external HTTP server to verify if the call should be aborted based on header value sent by the Pyron client.

The plugin rule configuration has following form:

```json
{
  "rules": [
    {
      "default": {
        "targetHost": "example.com",
        "targetPort": 8000
      },
      "endpoints": [
        {
          "method": "POST",
          "pathPattern": "/user",
          "requestPlugins": [
            {
              "name": "sample-abort",
              "conf": {
                "header": "Authorization"
              }
            }
          ]
        }
      ]
    }
  ]
}
```

and verticle configuration:
```json
{
  "verticleConfig": {
    "path": "/verify",
    "client": ...
  }
}
```

`path` attribute defines the path of external server that will be called. `client` defines `SmartHttpClient` configuration.

#### Plugin implementation

```java
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
```

#### Plugin module configuration

Add client configuration at `verticleConfig.client` path in plugin module configuration:

```json
{
  "registry:request-plugins": {
    "abort": {
      "main": "com.cloudentity.pyron.sample.java.AbortPluginVerticle",
      "verticleConfig": {
        "path": "$env:PLUGIN_ABORT__PATH:string:/verify",
        "client": {
          "serviceLocation": {
            "host": "$env:PLUGIN_ABORT__HOST:string",
            "port": "$env:PLUGIN_ABORT__PORT:int",
            "ssl": "$env:PLUGIN_ABORT__SSL:boolean"
          }
        }
      }
    }
  }
}
```