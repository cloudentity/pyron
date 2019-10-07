## Working with immutable RequestCtx and ResponseCtx

The plugin logic is implemented in

* `def apply(ctx: RequestCtx, conf: C): Future[RequestCtx]`
* `def apply(ctx: ResponseCtx, conf: C): Future[ResponseCtx]`.

According to method signature, the `apply` method accepts `RequestCtx` (or `ResponseCtx`) with some configuration `C` and returns `RequestCtx` asynchronously.
The reason for accepting and returning `RequestCtx` is the fact that it is immutable. Any method executed on it does not affect it but returns a new modified instance of `RequestCtx`.

Plugin `apply` method needs to return the final `RequestCtx` with all modifications applied.

Additionally, `RequestCtx` (and `ResponseCtx`) aggregates several immutable values, e.g. target request, extra access log items, properties, authentication context, etc.
`RequestCtx` provides `modifyXXX` methods to make modifying nested values easier. E.g. `RequestCtx.modifyProperties` returns new `RequestCtx` with modified properties.

We can chain `modify...` calls to get the final `RequestCtx`. `modifyX` method accepts a function that takes the current value of `X`, modifies it and returns new value of `X`.

Let's set extra access log item:

```scala
override def apply(requestCtx: RequestCtx, conf: Unit): Future[RequestCtx] =
  Future.successful {
    val finalCtx =
      requestCtx.modifyAccessLog { accessLogs =>
        accessLogs.updated("sample-plugin-applied", Json.fromBoolean(true))
      }

    finalCtx
  }
```

and at the same time set an authentication context value:

```scala
override def apply(requestCtx: RequestCtx, conf: Unit): Future[RequestCtx] =
  Future.successful {
    val finalCtx =
      requestCtx.modifyAccessLog { accessLogs =>
        accessLogs.updated("sample-plugin-applied", Json.fromBoolean(true))
      }.modifyAuthnCtx { authnCtx =>
        authnCtx.updated("user-id", Json.fromString("123"))
      }

    finalCtx
  }
```

Note that the following code will only modify authentication context:

```scala
override def apply(requestCtx: RequestCtx, conf: Unit): Future[RequestCtx] =
  Future.successful {
    requestCtx.modifyAccessLog { accessLogs => // this modification has no effect
      accessLogs.updated("sample-plugin-applied", Json.fromBoolean(true))
    }

    val finalCtx =
      modifyAuthnCtx { authnCtx =>
        authnCtx.updated("user-id", Json.fromString("123"))
      }

    finalCtx
  }
```

In final example, let's drop a header from target request. It's a bit more complex, because we need to modify 2 immutable values: target request and its headers.

```scala
override def apply(requestCtx: RequestCtx, conf: Unit): Future[RequestCtx] =
  Future.successful {
    val finalCtx =
      requestCtx.modifyRequest { request =>
        request.modifyHeaders { headers =>
          headers.remove("Authorization")
        }
      }

    finalCtx
  }
```

