## Working with immutable RequestCtx and ResponseCtx

The plugin logic is implemented in

* `def applyJava(RequestCtx ctx, JsonObject conf): Future[RequestCtx]`
* `def applyJava(ResponseCtx ctx, JsonObject conf): Future[ResponseCtx]`.

According to method signature, the `apply` method accepts `RequestCtx` (or `ResponseCtx`) with some configuration and returns `RequestCtx` asynchronously.
The reason for accepting and returning `RequestCtx` is the fact that it is immutable. Any method executed on it does not affect it but returns a new modified instance of `RequestCtx`.

Plugin `apply` method needs to return the final `RequestCtx` with all modifications applied.

Additionally, `RequestCtx` (and `ResponseCtx`) aggregates several immutable values, e.g. target request, extra access log items, properties, authentication context, etc.
`RequestCtx` provides `modifyXXX` methods to make modifying nested values easier. E.g. `RequestCtx.modifyProperties` returns new `RequestCtx` with modified properties.

We can chain `modify...` calls to get the final `RequestCtx`. `modifyX` method accepts a function that takes the current value of `X`, modifies it and returns new value of `X`.

Let's set extra access log item:

```java
  public Future<RequestCtx> applyJava(RequestCtx requestCtx, JsonObject conf) {
    RequestCtx finalCtx =
      requestCtx.modifyAccessLog(accessLogs -> {
        return accessLogs.updated("sample-plugin-applied", Json.fromBoolean(true));
      });

    return finalCtx;
  }
```

and at the same time set an authentication context value:

```java
  public Future<RequestCtx> applyJava(RequestCtx requestCtx, JsonObject conf) {
    RequestCtx finalCtx =
      requestCtx.modifyAccessLog(accessLogs -> {
        return accessLogs.updated("sample-plugin-applied", Json.fromBoolean(true));
      }).modifyAuthnCtx(authnCtx -> {
        return authnCtx.updated("user-id", Json.fromString("123"));
      });

    return finalCtx;
  }
```

Note that the following code will only modify authentication context:


```java
  public Future<RequestCtx> applyJava(RequestCtx requestCtx, JsonObject conf) {
    requestCtx.modifyAccessLog(accessLogs -> { // this modification has no effect
      return accessLogs.updated("sample-plugin-applied", Json.fromBoolean(true));
    });

    RequestCtx finalCtx =
      requestCtx.modifyAuthnCtx(authnCtx -> {
        return authnCtx.updated("user-id", Json.fromString("123"));
      });

    return finalCtx;
  }
```

In final example, let's drop a header from target request. It's a bit more complex, because we need to modify 2 immutable values: target request and its headers.

```java
  public Future<RequestCtx> applyJava(RequestCtx requestCtx, JsonObject conf) {
    RequestCtx finalCtx =
      requestCtx.modifyRequest(request -> {
        return request.modifyHeaders(headers -> {
          return headers.remove("Authorization");
        });
      });

    return finalCtx;
  }
```

