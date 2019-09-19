## Build

Edge depends on github.com/cloudentity/vertx-tools. Clone it and build with `mvn install` command first.

### Standalone

`mvn clean install -Pbuild-standalone`

## Run

### Standalone

cd run/standalone
./run.sh

If required set environment variables in `.env` file.

## Configuration

At the start Edge needs `meta-config.json` file describing where to read configuration from.

```json
{
  "scanPeriod": 5000,
  "stores": [
    {
      "type": "classpath",
      "format": "json",
      "config": {
        "path": "config.json"
      }
    },
    {
      "type": "file",
      "format": "json",
      "config": {
        "path": "path/to/rules.json"
      }
    }
  ]
}
```

Above `meta-config.json` defines two configuration stores: `config.json` from classpath and `rules.json` from file system.

`config.json` defines minimal configuration required to run Edge without routing rules that are provided in `rules.json`.

### Routing rules

Rule defines routing to a target endpoint. Rules are grouped in blocks that share common attributes in `default` object.
If an endpoint attribute is missing then it is taken from `default`.

```json
{
  "rules": [
    {
      "default": {
        "targetHost": "example.com",
        "targetPort": 8000
      },
      "endpoints": [
        ...
      ]
    }
  ]
}
```

#### Method and path pattern

```json
{
  "endpoints": [
    {
      "method": "POST",
      "pathPattern": "/user"
    }
  ]
}
```

`pathPattern` is regular expression extended with support of path-param placeholders, e.g. `/user/{user_id}`

#### Path prefix

Expose multiple endpoints using the same path prefix.

```json
{
  "rules": [
    {
      "default": {
        "targetHost": "example.com",
        "targetPort": 8000,
        "pathPrefix": "/v1",
        "dropPathPrefix": true // default
      },
      "endpoints": [
        {
          "method": "POST",
          "pathPattern": "/user"
        },
        {
          "method": "GET",
          "pathPattern": "/user/{user_id}"
        }
      ]
    }
  ]
}
```

By default the prefix is dropped when calling target service. I.e. endpoint exposed at `POST /v1/user` is proxied to `POST /user` in target service.
To preserve the prefix set `dropPathPrefix` to false.

#### Rewrite path

```json
{
  "endpoints": [
    {
      "method": "GET",
      "pathPattern": "/user/{user_id}",
      "rewritePath": "/entities/user/{user_id}"
    }
  ]
}
```

#### Rewrite method

```json
{
  "endpoints": [
    {
      "method": "POST",
      "rewriteMethod": "PUT",
      "pathPattern": "/user"
    }
  ]
}
```

#### Response timeout

```json
{
  "endpoints": [
    {
      "method": "POST",
      "pathPattern": "/user",
      "call": {
        "responseTimeout": 3000
      }
    }
  ]
}
```

Response timeout in milliseconds.

#### Retry

```json
{
  "endpoints": [
    {
      "method": "POST",
      "pathPattern": "/user",
      "call": {
        "retries": 1,
        "failureHttpCodes": [500],
        "retryFailedResponse": true, // default
        "retryOnException": true     // default
      }
    }
  ]
}
```

Edge retries call if target service returned HTTP status code defined in `failureHttpCodes` or on HTTP client exception (e.g. response timeout).
`retries` defines maximum number of retries; if reached then the last attempt result is returned to the client.

#### Preserve Host header

```json
{
  "endpoints": [
    {
      "method": "POST",
      "pathPattern": "/user",
      "preserveHostHeader": true
    }
  ]
}
```

By default Edge uses target host when calling target service. When `preserveHostHeader` is set to true then the Host header sent by the client is used instead.

### Service discovery

Edge Gateway provides support for service discovery utilizing Consul client or configuration object.

#### Target service

```json
{
  "rules": [
    {
      "default": {
        "targetService": "example-service"
      },
      "endpoints": [
        ...
      ]
    }
  ]
}
```

Edge calls nodes with `targetService` service-name using round-robin load balancer.

#### Consul service discovery

Add `sd-provider/consul` to `MODULES` environment variable, i.e. `MODULES=["sd-provider/consul"]`.

| Env variable          | Description                          |
|-----------------------|--------------------------------------|
| CONSUL_HOST           | host                                 |
| CONSUL_POST           | port (default 8500)                  |
| CONSUL_SSL            | SSL flag (default false)             |
| CONSUL_ACL_TOKEN      | ACL token                            |
| CONSUL_DC             | data center                          |
| CONSUL_TIMEOUT        | connection timeout                   |
| CONSUL_SD_SCAN_PERIOD | nodes refresh period in milliseconds |

Note: nodes registered in Consul need to have `http-endpoint` tag and `ssl` tag if exposed over SSL.

#### Configuration-based service discovery

Add `sd-provider/static` to `MODULES` environment variable, i.e. `MODULES=["sd-provider/static"]`.

Add `sd-records` configuration attribute (e.g. in `system.json` file).

```json
{
  "sd-records": [
    {
      "name": "example-service",
      "location": {
        "host": "example.com",
        "port": 8000,
        "ssl": false,
        "root": "/v1" // default ''
      }
    }
  ]
}
```

### HTTP server

Edge uses Vertx HTTP server implementation. Use environment variables to configure `io.vertx.core.http.HttpServerOptions`.

Environment variables map to `HttpServerOptions` attributes in following way:

* All variable names start with `HTTP_SERVER_` prefix,
* HttpServerOptions attribute name is capitalized and camel-case is replaced with underscore `_`.
* If an attribute has object value then it's sub-attribute env names are prefixed with `HTTP_SERVER_{parent-env-name}__` (note double underscore).

Examples:

| Name                                            | HttpServerOptions attribute |
|-------------------------------------------------|-----------------------------|
| `HTTP_SERVER_PORT`                              | port                        |
| `HTTP_SERVER_ACCEPT_BACKLOG`                    | acceptBacklog               |
| `HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_PATHS`     | pemTrustOptions.certPaths   |

#### HTTP server collection options

In order to set `HttpServerOptions` attribute with collection value use JSON syntax, e.g. `HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_PATHS=["/etc/ssl/cert.pem"]`.

### HTTP clients

Edge uses Vertx implementation of HTTP clients. Use environment variables to configure default `io.vertx.core.http.HttpClientOptions`.

Environment variables map to `HttpClientOptions` attributes the same way they map to `HttpServerOptions`.

You can configure HTTP client for each target service separately:

```json
{
  "smart-http-target-clients": {
    "example-service": {
      "http": {
        // io.vertx.core.http.HttpClientOptions
        "maxPoolSize": 50
        ...
      }
    }
  }
}
```

#### Default retries and timeout

```json
{
  "smart-http-target-clients": {
    "example-service": {
      "retries": 5,
      "responseTimeout": 3000,
      "failureHttpCodes": [500],
      "retryFailedResponse": false,
      "retryOnException": false
    }
  }
}
```

Target client retry and timeout default attributes are overridden by values set in routing rule.

#### Circuit breaker

Configure `circuitBreaker` to enable circuit breaker functionality of target service client.

```json
{
  "smart-http-target-clients": {
    "example-service": {
      "circuitBreaker": {
        // io.vertx.circuitbreaker.CircuitBreakerOptions
        "maxFailures": 3
        ...
      }
    }
  }
}
```