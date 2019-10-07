## Call external HTTP server from a Scala plugin

This how-to assumes that you read Scala plugin development [guide](plugin-dev-scala.md).
The samples used here can be found in `sample-scala-plugins` module.

We will use `SmartHttpClient` provided by [vertx-client](https://github.com/Cloudentity/vertx-tools) library.
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

```scala
var client: SmartHttpClient = _

override def initServiceAsyncS(): Future[Unit] = {
  // assuming that smart-client configuration is in `client` attribute of verticle config
  val clientConfig: JsonObject = getConfig().getJsonObject("client")

  SmartHttp.clientBuilder(vertx, clientConfig).build().toScala.map(c => client = c)
}
```

> NOTE<br/>
> `.toScala` and `.toJava` helper methods transform `scala.concurrent.Future` with `io.vertx.core.Future` back and forth.

<a id="use"></a>
### How to use client

`client.METHOD()` (e.g. `client.get()`) returns a request builder. Calling `.end` or `.endWithBody` on it triggers the HTTP call.

Use `.end` if you don't care about response body:

```scala
val client: SmartHttpClient = ???

client.get("/path")
  .end().toScala()
  .map { response =>
    if (response.statusCode() == 200) {
      requestCtx
    } else {
      requestCtx.abort(ApiResponse(403, Buffer.buffer(), Headers()))
    }
  }
```

Otherwise use `.endWithBody`:

```scala
val client: SmartHttpClient = ???

client.get("/path")
  .endWithBody().toScala()
  .map { response =>
    if (response.getHttp().statusCode() == 200) {
      requestCtx
    } else {
      requestCtx.abort(ApiResponse(403, response.getBody(), Headers()))
    }
  }
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

```scala
case class AbortConf(header: String)
case class AbortPluginVerticleConf(path: String, client: JsonObject)

class AbortPluginVerticle extends RequestPluginVerticle[AbortConf] with ConfigDecoder {
  override def name: PluginName = PluginName("sample-abort")

  var verticleConf: AbortPluginVerticleConf = _
  var client: SmartHttpClient = _

  override def initServiceAsyncS(): Future[Unit] = {
    implicit val PluginConfDecoder = deriveDecoder[AbortPluginVerticleConf]

    verticleConf = decodeConfigUnsafe[AbortPluginVerticleConf]
    SmartHttp.clientBuilder(vertx, verticleConf.client)
      .build().toScala.map(c => client = c)
  }

  override def apply(requestCtx: RequestCtx, conf: AbortConf): Future[RequestCtx] = {
    requestCtx.request.headers.get(conf.header) match {
      case Some(value) =>
        client.get(verticleConf.path)
          .putHeader(conf.header, value)
          .end().toScala()
          .map { response =>
            if (response.statusCode() == 200) {
              requestCtx
            } else {
              requestCtx.abort(ApiResponse(403, Buffer.buffer(), Headers()))
            }
          }
      case None =>
        Future.successful(
          requestCtx.abort(ApiResponse(403, Buffer.buffer(), Headers()))
        )
    }
  }

  override def validate(conf: AbortConf): ValidateResponse =
    if (conf.header.nonEmpty) {
      ValidateOk
    } else {
      ValidateFailure("'header' must not be empty")
    }

  override def confDecoder: Decoder[AbortConf] = deriveDecoder
}
```

#### Plugin module configuration

Add client configuration at `verticleConfig.client` path in plugin module configuration:

```json
{
  "registry:request-plugins": {
    "abort": {
      "main": "com.cloudentity.pyron.sample.scala.AbortPluginVerticle",
      "verticleConfig": {
        "path": "/verify",
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