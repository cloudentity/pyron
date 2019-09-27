## Contents

* [Build](#build)
  * [Standalone](#build-standalone)
  * [Docker](#build-docker)
* [Run](#run)
  * [Standalone](#run-standalone)
  * [Docker](#run-docker)
* [Configure](#configure)
  * [Routing rules](#routing)
    * Method and path pattern
    * Path prefix
    * Rewrite path
    * Rewrite method
    * Response timeout
    * Retry
    * Preserve Host header
  * [Plugins](#plugins)
    * Authentication
      * OAuth 2 with JWT access token
  * [API groups](#api-groups)
  * [Service discovery](#service-discovery)
    * Consul service discovery
    * Configuration-based service discovery
    * Self-registration in Consul
  * [HTTP server](#http-server)
  * [HTTP clients](#http-clients)
    * Default retries and timeout
    * Circuit breaker
  * [Open tracing](#open-tracing)
  * [Access log](#access-log)
    * Authentication context and request headers in access log
  * [Proxy headers](#proxy-headers)

## Build

Edge depends on bitbucket.org/syntegritynet/open-vertx-tools. Clone it and build with `mvn install` command first.

### Standalone

`mvn clean install -Pbuild-standalone`

### Docker

`mvn clean install -Pbuild-latest-docker`

## Run

Configure routing rules in `rules.json` and environment variables in `envs` file if required.

By default Edge runs on 8080 port. Set `HTTP_SERVER_PORT` env variable to change it.

### Standalone

* `cd run/standalone`
* `./run.sh`

### Docker

* `cd run/docker`
* `docker run --env-file envs -p 8080:8080 --name edge -v "$(pwd)"/configs:/configs -d docker.artifactory.syntegrity.com/edge:latest`

## Configure

At startup Edge needs `meta-config.json` file describing where to read configuration from.

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

Above `meta-config.json` defines two configuration stores: `config.json` from JAR classpath and `rules.json` from file system.

`config.json` defines minimal configuration required to run Edge. Routing rules are provided in `rules.json`.

You will find `meta-config.json` in run folder (`run/standalone` or `run/docker`).

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

| Attribute          | Description                                    |
|--------------------|------------------------------------------------|
| targetHost         | host of target service (upstream)              |
| targetPort         | port of target service (upstream)              |

#### Method and path pattern

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
          "pathPattern": "/user"
        }
      ]
    }
  ]
}
```

| Attribute          | Description                                                                            |
|--------------------|----------------------------------------------------------------------------------------|
| method             | HTTP method                                                                            |
| pathPattern        | regular expression extended with support of path-param placeholders, e.g. `/user/{id}` |

#### Path prefix

Expose multiple endpoints using the same path prefix.

```json
{
  "rules": [
    {
      "default": {
        "targetHost": "example.com",
        "targetPort": 8000,
        "pathPrefix": "/example",
        "dropPrefix": true // default
      },
      "endpoints": [
        {
          "method": "POST",
          "pathPattern": "/user"
        },
        {
          "method": "GET",
          "pathPattern": "/user/{id}"
        }
      ]
    }
  ]
}
```

| Attribute          | Description                                                   |
|--------------------|---------------------------------------------------------------|
| pathPrefix         | prefix appended to `pathPattern` (optional)                   |
| dropPrefix         | drop path prefix when calling target service (default true)   |

By default the prefix is dropped when calling target service. I.e. endpoint exposed at `POST /example/user` is proxied to `POST /user` in target service.
To preserve the prefix set `dropPrefix` to false.

#### Rewrite path

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
          "method": "GET",
          "pathPattern": "/user/{id}",
          "rewritePath": "/entities/user/{id}"
        }
      ]
    }
  ]
}
```

| Attribute          | Description                                                                            |
|--------------------|----------------------------------------------------------------------------------------|
| rewritePath        | path that Edge calls target service at (optional, `pathPattern` used if this not set)  |

#### Rewrite method

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
              "rewriteMethod": "PUT",
              "pathPattern": "/user"
            }
      ]
    }
  ]
}
```

| Attribute          | Description                                                                            |
|--------------------|----------------------------------------------------------------------------------------|
| rewriteMethod      | method that Edge calls target service with (optional, `method` used if this not set)   |

#### Response timeout

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
          "call": {
            "responseTimeout": 3000
          }
        }
      ]
    }
  ]
}
```

| Attribute            | Description                                                                            |
|----------------------|----------------------------------------------------------------------------------------|
| call.responseTimeout | target service response timeout in milliseconds                                        |

#### Retry

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
          "call": {
            "retries": 1,
            "failureHttpCodes": [500],
            "retryFailedResponse": true, // default
            "retryOnException": true     // default
          }
        }
      ]
    }
  ]
}
```

| Attribute                | Description                                                                            |
|--------------------------|----------------------------------------------------------------------------------------|
| call.retries             | maximum number of retries                                                              |
| call.failureHttpCodes    | response codes that Edge retries if returned by target service                         |
| call.retryFailedResponse | retry call if target service returned code in `failureHttpCodes` (default true)        |
| call.retryOnException    | retry call on HTTP client exception, e.g. response timeout (default true)              |

#### Preserve Host header

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
          "preserveHostHeader": true
        }
      ]
    }
  ]
}
```

| Attribute            | Description                                                            |
|----------------------|------------------------------------------------------------------------|
| preserveHostHeader   | proxy Host header sent by the client to target service (default false) |

By default Edge sends target host in Host header to target service, set `preserveHostHeader` to true to send Host header sent by the client instead.

### Plugins

#### Authentication

`authn` plugin performs authentication and optionally sets entities in the request authentication context.

Enable `authn` plugin by adding `plugin/authn` to `MODULES` environment variable.

```json
{
  "endpoints": [
    {
      "method": "POST",
      "pathPattern": "/user",
      "requestPlugins": [
        {
          "name": "authn",
          "conf": {
            "methods": [ ... ],
            "entities": [ ... ]
          }
        }
      ]
    }
  ]
}
```

Configuration attributes:

| Name               | Description                                                                     |
|--------------------|---------------------------------------------------------------------------------|
| `methods`          | list of enabled authentication methods                                          |
| `entities`         | required entities set in authentication context                                 |
| `tokenHeader`      | name of the HTTP header containing authentication token (default Authorization) |

##### OAuth 2 with JWT access token

Enable OAuth 2 authentication method for JWT access tokens by adding `plugin/authn/oauth2` to `MODULES` environment variable.
This module enables `oauth2` authentication method with `jwt` entity provider. `jwt` provider sets all JWT claims in authentication context.

Token header sent by the client should have following format: `Bearer {access-token}`.

```json
{
  "endpoints": [
    {
      "method": "POST",
      "pathPattern": "/user",
      "requestPlugins": [
        {
          "name": "authn",
          "conf": {
            "methods": ["oauth2"],
            "entities": ["jwt"]
          }
        }
      ]
    }
  ]
}
```

Configure OIDC server:

| Env variable          | Description                                    |
|-----------------------|------------------------------------------------|
| OIDC_HOST             | OIDC server host                               |
| OIDC_PORT             | OIDC server port                               |
| OIDC_SSL              | SSL enabled (default false)                    |
| OIDC_JWK_ENDPOINT     | public server JSON Web Key endpoint            |

### API Groups

API Groups allow to separate routing rule sets. You can define a set of rules and expose it on a domain and/or base-path.
Incoming request is initially matched against domain and base-path and then dispatched to appropriate set for
further processing.

```json
{
  "apiGroups": {
    "example": {
      "_group": {
        "domains": ["demo.com"],
        "basePath": "/apis"
      },
      "_rules": [
        {
          "default": {
            "targetHost": "example.com",
            "targetPort": 8080
          },
          "endpoints": [
            {
              "method": "GET",
              "pathPattern": "/user"
            }
          ]
        }
      ]
    }
  }
}
```

| Attribute                | Description                                        |
|--------------------------|----------------------------------------------------|
| _group.domains           | Host headers Edge matches the API group for        |
| _group.basePath          | base path Edge matches the API group at (optional) |

Note `_` (underscore) in `_rules` and `_group`.

[API Groups configuration details.](docs/api-groups.md)

### Service discovery

Edge Gateway provides support for service discovery utilizing Consul client or configuration object.

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

| Attribute          | Description                                         |
|--------------------|-----------------------------------------------------|
| targetService      | service-name of target nodes from service-discovery |

Edge calls nodes with `targetService` service-name using round-robin load balancer.

Below you will find instructions how to enable service discovery provider.

#### Consul service discovery

Add `sd-provider/consul` to `MODULES` environment variable, i.e. `MODULES=["sd-provider/consul"]`.

| Env variable          | Description                                         |
|-----------------------|-----------------------------------------------------|
| CONSUL_HOST           | host                                                |
| CONSUL_POST           | port (default 8500)                                 |
| CONSUL_SSL            | SSL enabled (default false)                         |
| CONSUL_ACL_TOKEN      | ACL token (optional)                                |
| CONSUL_DC             | data center (optional)                              |
| CONSUL_TIMEOUT        | connection timeout (optional)                       |
| CONSUL_SD_SCAN_PERIOD | nodes refresh period in milliseconds (default 2000) |

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

| Attribute          | Description                          |
|--------------------|--------------------------------------|
| name               | service-name of target node          |
| location.host      | host of target node                  |
| location.port      | port of target node                  |
| location.ssl       | SSL of target node                   |
| location.root      | root path of target node (optional ) |

#### Self-registration in Consul

Edge node can register itself in Consul for service discovery.

Add `sd-registrar/consul` to `MODULES` environment variable, i.e. `MODULES=["sd-registrar/consul"]`.

| Env variable                 | Description                                                 |
|------------------------------|-------------------------------------------------------------|
| CONSUL_HOST                  | host                                                        |
| CONSUL_POST                  | port (default 8500)                                         |
| CONSUL_SSL                   | SSL enabled (default false)                                 |
| CONSUL_ACL_TOKEN             | ACL token (optional)                                        |
| CONSUL_DC                    | data center (optional)                                      |
| CONSUL_TIMEOUT               | connection timeout (optional)                               |
| CONSUL_SD_SCAN_PERIOD        | nodes refresh period in milliseconds (default 2000)         |
| SELF_SD_SERVICE_NAME         | Edge service name                                           |
| SELF_SD_HOST                 | host of Edge node                                           |
| SELF_SD_PORT                 | port of Edge node                                           |
| SELF_SD_SSL                  | ssl of Edge node (default false)                            |
| SELF_SD_HEALTHCHECK_HOST     | host of Edge health-check (default SELF_SD_SERVICE_NAME)    |
| SELF_SD_HEALTHCHECK_PORT     | port of Edge health-check (default SELF_SD_HEALTHCHECK_PORT)|
| SELF_SD_HEALTHCHECK_PATH     | path of Edge health-check (default /alive)                  |
| SELF_SD_HEALTHCHECK_INTERVAL | health-check interval (default 3s)                          |
| SELF_SD_DEREGISTER_AFTER     | node de-register period when health-check fails (600s)      |
| SELF_SD_TAGS                 | extra node tags (default [])                                |

### HTTP server

Edge uses Vertx HTTP server implementation. Use environment variables to configure `io.vertx.core.http.HttpServerOptions`.

Environment variables map to `HttpServerOptions` ([see docs](https://vertx.io/docs/apidocs/io/vertx/core/http/HttpServerOptions.html)) attributes in following way:

* All variable names start with `HTTP_SERVER_` prefix,
* HttpServerOptions attribute name is capitalized and camel-case is replaced with underscore `_`.
* If an attribute has object value then it's sub-attribute env names are prefixed with `HTTP_SERVER_{parent-env-name}__` (note double underscore).

Examples:

| Name                                            | HttpServerOptions attribute                                                                                                                                |
|-------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| HTTP_SERVER_PORT                                | [port](https://vertx.io/docs/apidocs/io/vertx/core/http/HttpServerOptions.html#setPort-int-)                                                               |
| HTTP_SERVER_ACCEPT_BACKLOG                      | [acceptBacklog](https://vertx.io/docs/apidocs/io/vertx/core/http/HttpServerOptions.html#setAcceptBacklog-int-)                                            |
| HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_PATHS       | [pemTrustOptions.certPaths](https://vertx.io/docs/apidocs/io/vertx/core/http/HttpServerOptions.html#setPemTrustOptions-io.vertx.core.net.PemTrustOptions-) |

In order to set `HttpServerOptions` attribute with collection value use JSON syntax, e.g. `HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_PATHS=["/etc/ssl/cert.pem"]`.

### HTTP clients

Edge uses Vertx implementation of HTTP clients. Use environment variables to configure default `io.vertx.core.http.HttpClientOptions`.

Environment variables map to `HttpClientOptions` ([see docs](https://vertx.io/docs/apidocs/io/vertx/core/http/HttpClientOptions.html)) attributes the same way they map to `HttpServerOptions`.

You can configure HTTP client for each target service separately (note that default attributes from environment variables are ignored in this case):

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
      "responseTimeout": 3000,
      "retries": 5,
      "failureHttpCodes": [500]
    }
  }
}
```

Target client retry and timeout default attributes are overridden by values set in routing rule.

#### Circuit breaker

Configure `io.vertx.circuitbreaker.CircuitBreakerOptions` in `circuitBreaker` object to enable circuit breaker functionality per target service.

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

### Open tracing

Add `tracing/jaeger` to `MODULES` environment variable, i.e. `MODULES=["tracing/jaeger"]`.

| Env variable                      | Description                                    |
|-----------------------------------|------------------------------------------------|
| TRACING_SERVICE_NAME              | Edge name in Jaeger                            |
| JAEGER_AGENT_HOST                 | Jaeger agent host                              |
| JAEGER_AGENT_PORT                 | Jaeger agent port (optional)                   |
| JAEGER_SAMPLER_MANAGER_HOST_PORT  | Jaeger sampler host:port (optional)            |
| TRACING_FORMAT                    | tracing format - cloudentity, jaeger, zipkin   |

### Access logs

```json
{
  "timestamp": "2018-04-06T15:14:33.929Z",
  "trueClientIp": "192.168.0.13",
  "remoteClient": "192.168.0.127",
  "http": {
    "httpVersion": "HTTP/1.1",
    "method": "GET",
    "uri": "/service-a/user/123",
    "status": "200"
  },
  "gateway": {
    "method": "GET",
    "path": "/user/{userId}",
    "pathPrefix": "/service-a",
    "aborted": false,
    "targetService": "service-a"
  },
  "request": {
    "headers": {
      "Host": ["example.com"]
    }
  },
  "authnCtx": {
      "method": "oauth2",
      "user":"4b1b17f8-a934-458f-3c08-cc01d9f9b917",
      "uid":"admin@cloudentity.com"
    }
  },
  "timeMs": "3"
}
```

| Attribute             | Description                                                                                               |
|-----------------------|-----------------------------------------------------------------------------------------------------------|
| datetime              | request time in ISO 8601 format                                                                           |
| trueClientIp          | IP address of original client, either X-Real-IP header or first IP from X-Forwarded-For or remote address |
| remoteIp              | IP of the direct client                                                                                   |
| authnCtx              | authentication context                                                                                    |
| httpVersion           | HTTP version                                                                                              |
| method                | HTTP method                                                                                               |
| uri                   | URI without host                                                                                          |
| status                | HTTP status code                                                                                          |
| gateway.method        | method of matching rule                                                                                   |
| gateway.path          | path pattern of matching rule                                                                             |
| gateway.pathPrefix    | path prefix of matching rule                                                                              |
| gateway.aborted       | true if Edge aborted the call without proxying to target service; false otherwise                         |
| gateway.targetService | target service of matching rule                                                                           |
| request.headers       | request headers                                                                                           |
| timeMs                | time from receiving the request body till writing full response body                                      |

#### Authentication context and request headers in access log

| Env variable                            | Description                                         | Example                                             |
|-----------------------------------------|-----------------------------------------------------|-----------------------------------------------------|
| ACCESS_LOG_SLF4J_DISABLED               | disable SLF4J access logging (default false)        | true                                                |
| ACCESS_LOG_AUTHN_CTX                    | authentication context set in access log (optional) | {"method":"authnMethod","user":"sub","uid":"email"} |
| ACCESS_LOG_REQUEST_HEADERS_ALL          | log all headers flag (default false)                | true                                                |
| ACCESS_LOG_REQUEST_HEADERS_WHITELIST    | log selected headers (optional)                     | ["Host","User-Agent"]                               |
| ACCESS_LOG_REQUEST_HEADERS_MASK_WHOLE   | mask whole logged header (optional)                 | ["Authorization"]                                   |
| ACCESS_LOG_REQUEST_HEADERS_MASK_PARTIAL | mask logged header partially (optional)             | ["Token"]                                           |

### Proxy headers

Edge applies following request headers modification (unless disabled):

* Add `remote-address.host` to `X-Forwarded-For` headers
* Add `remote-address.protocol` to `X-Forwarded-Proto` headers
* If `Host` header is set add it to `X-Forwarded-Host` headers
* If True Client IP header is missing then set it to first `X-Forwarded-For` value
* Set True Client IP header to upstream service

| Env variable                      | Description                                            |
|-----------------------------------|--------------------------------------------------------|
| PROXY_HEADERS_ENABLED             | enable proxy headers (default true)                    |
| INPUT_TRUE_CLIENT_IP_HEADER       | True Client IP header name (default X-Real-IP)         |
| OUTPUT_TRUE_CLIENT_IP_HEADER      | Outgoing True Client IP header name (default X-Real-IP)|
