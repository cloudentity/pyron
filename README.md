[![][cloudentity-logo]](https://cloudentity.com)

* [Introduction](#intro)
* [Build](#build)
  * [Standalone](#build-standalone)
  * [Docker](#build-docker)
* [Run](#run)
  * [Standalone](#run-standalone)
  * [Docker](#run-docker)
* [Configure](#config)
  * [Meta config](#config-meta)
  * [Routing rules](#config-routing)
  * [Plugins](#config-plugins)
  * [API groups](#config-api-groups)
  * [Service discovery](#config-service-discovery)
  * [HTTP server](#config-http-server)
  * [HTTP clients](#config-http-clients)
  * [Open tracing](#config-open-tracing)
  * [Access log](#config-access-log)
  * [Proxy headers](#config-proxy-headers)
* [Performance](#performance)

<a id="intro"></a>
## Introduction

Cloudentity Edge Gateway provides the dividing line between the Client (such as a Browser, Mobile App, Other 3rd party services) and the trusted mesh of services, microservices or applications that are deployed on-prem, cloud, hybrid or multi-cloud environments.

### Supported functionality

#### API endpoints publishing
The Edge provides a number of tools to manage, transform, and secure your API endpoints:

* *Routing*: API calls can be routed to different targets based on the URI path pattern
* *Filtering and Orchestration*: API endpoint level URI rewrite and the ability to assign filters/plugins both at the service level as well as the individual API endpoint level can be defined by the URI pattern matching
* *Header Management*: Support for X-Forwarded-For, X-TrueClient-IP, Proxy VIA headers
* *OpenTracing support*: provides visibility into cross-service communication and instrumentation enabling the distributed tracing.
* *API Specification Support*: Configuration for the published API endpoints can be configured via any combination of following methods
  * JSON or YAML files
  * Consul Key-Value pair database ◦ Cloudentity Application Service

#### Authentication
As an enforcement point, Edge integrates with a wide range of protocols and tools to ensure the request is not only authorized but also secure in the context of a wide range of risk and business rules.

* *Protocol Support*
  * OAuth2.0 JWT Access Token
  * Anonymous authentication – ability to track public request
* *Fallback Authorization*: Edge has the ability to chain multiple authentication methods together and define the fallback scenarios Request authorization.

#### Integration with protected services

The Cloudentity Edge can normalize your API by transforming and managing requests to the services it protects.

* *Service Management*: Ability to configure/discover the location of the protected services via
  * Consul Service discovery
  * Static Service registry – provided as part of the Edge API configuration (external KV or JSON flat file). Support for multiple nodes per service name to allow client based load balancing.
* *Service Target Management*: Direct target host configuration as part of the API publishing rules
* *Load Balancing*: When using Consul or Fixed Service registry, Edge can provide load balancing to targets
* *SMART HTTP client functionality*
  * API Request retries
  * Request failover
  * Circuit Breaker support
  * Open Tracing support
  * Per service connection pooling support
  * Per service connection keep-alive support
* *URI Rewrite*: Provide backward compatibility or consistency by rewriting the URI proxied to protected service

#### API protection

Cloudentity Edge also provides broad API protection with a number of standard features.

* URI structure enforcement and whitelisting
* Detailed access logs including the authentication context
* Ability to configured desired TLS version and ciphers

#### Extensibility

Edge also allows for custom plugins which can be used to integrate legacy or proprietary systems as part of the standard data flow and enforcement. This could include custom callouts, complex business logic, or custom protocol/security management.

<a id="build"></a>
## Build

Edge depends on https://bitbucket.org/syntegritynet/open-vertx-tools. Clone it and build with `mvn install` command first.

#### Prerequisites

* Maven 3+
* JDK 1.8+

<a id="build-standalone"></a>
### Standalone

```
$ mvn clean install -Pbuild-standalone
```

<a id="build-docker"></a>
### Docker

```
$ mvn clean install -Pbuild-latest-docker
```

<a id="run"></a>
## Run

Configure routing rules in `rules.json` and environment variables in `envs` file if required.

By default Edge runs on 8080 port. Set `HTTP_SERVER_PORT` env variable to change it.

<a id="run-standalone"></a>
### Standalone

```
$ cd run/standalone
$ ./run.sh
```
<a id="run-docker"></a>
### Docker

```
$ cd run/docker
$ docker run --env-file envs --network="host" --name edge -v "$(pwd)"/configs:/configs -d docker.artifactory.syntegrity.com/edge:latest
```

<a id="config"></a>
## Configure

* [Meta config](#config-meta)
* [Routing rules](#config-routing)
  * [Method and path pattern](#config-)
  * [Path prefix](#config-path-prefix)
  * [Rewrite path](#config-rewrite-path)
  * [Rewrite method](#config-rewrite-method)
  * [Response timeout](#config-response-timeout)
  * [Retry](#config-retry)
  * [Preserve Host header](#config-preserve-host--header)
* [Plugins](#config-plugins)
  * Authentication
    * OAuth 2 with JWT access token
* [API groups](#config-api-groups)
* [Service discovery](#config-service-discovery)
  * Consul service discovery
  * Configuration-based service discovery
  * Self-registration in Consul
* [HTTP server](#config-http-server)
* [HTTP clients](#config-http-clients)
  * Default retries and timeout
  * Circuit breaker
* [Open tracing](#config-open-tracing)
* [Access log](#config-access-log)
  * Authentication context and request headers in access log
* [Proxy headers](#config-proxy-headers)

<a id="config-meta"></a>
### Meta config

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
        "path": "rules.json"
      }
    }
  ]
}
```

Above `meta-config.json` defines two configuration stores: `config.json` from JAR classpath and `rules.json` from the file system.

`config.json` defines minimal configuration required to run Edge. Routing rules are provided in `rules.json`.

You will find `meta-config.json` in the run folder (`run/standalone` or `run/docker`).

<a id="config-routing"></a>
### Routing rules

The rule defines routing to a target endpoint. Rules are grouped in blocks that share common attributes in the `default` object.
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
|:-------------------|:-----------------------------------------------|
| targetHost         | host of target service (upstream)              |
| targetPort         | port of target service                         |

<a id="config-method-path-pattern"></a>
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
|:-------------------|:---------------------------------------------------------------------------------------|
| method             | HTTP method                                                                            |
| pathPattern        | regular expression extended with support of path-param placeholders, e.g. `/user/{id}` |

<a id="config-path-prefix"></a>
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
        "dropPrefix": true
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
|:-------------------|:--------------------------------------------------------------|
| pathPrefix         | prefix appended to `pathPattern` (optional)                   |
| dropPrefix         | drop path prefix when calling target service (default true)   |

By default, the prefix is dropped when calling target service. I.e. endpoint exposed at `POST /example/user` is proxied to `POST /user` in target service.
To preserve the prefix set `dropPrefix` to false.

<a id="config-rewrite-path"></a>
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
|:-------------------|:---------------------------------------------------------------------------------------|
| rewritePath        | path that Edge calls target service at (optional, `pathPattern` used if this not set)  |

<a id="config-rewrite-method"></a>
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
|:-------------------|:---------------------------------------------------------------------------------------|
| rewriteMethod      | method that Edge calls target service with (optional, `method` used if this not set)   |

<a id="config-response-timeout"></a>
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
|:---------------------|:---------------------------------------------------------------------------------------|
| call.responseTimeout | target service response timeout in milliseconds                                        |

<a id="config-retry"></a>
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
            "retryFailedResponse": true,
            "retryOnException": true
          }
        }
      ]
    }
  ]
}
```

| Attribute                | Description                                                                            |
|:-------------------------|:---------------------------------------------------------------------------------------|
| call.retries             | maximum number of retries                                                              |
| call.failureHttpCodes    | response codes that Edge retries if returned by target service                         |
| call.retryFailedResponse | retry call if target service returned code in `failureHttpCodes` (default true)        |
| call.retryOnException    | retry call on HTTP client exception, e.g. response timeout (default true)              |

<a id="config-preserve-host-header"></a>
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

| Attribute            | Description                                                                        |
|:---------------------|:-----------------------------------------------------------------------------------|
| preserveHostHeader   | should send to target service Host header received from the client (default false) |

By default, Edge sends target host in Host header to target service, set `preserveHostHeader` to true to send Host header sent by the client instead.

<a id="config-plugins"></a>
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
|:-------------------|:--------------------------------------------------------------------------------|
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

| Env variable                         | Description                                                                                                                                  |
|:-------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------|
| OIDC_HOST                            | OIDC server host                                                                                                                             |
| OIDC_PORT                            | OIDC server port                                                                                                                             |
| OIDC_SSL                             | SSL enabled (default false)                                                                                                                  |
| OIDC_JWK_ENDPOINT                    | public server JSON Web Key endpoint path                                                                                                     |
| OIDC_TRUST_ALL                       | trust all OIDC SSL certificates (optional, default false)                                                                                                                   |
| OIDC_PEM_TRUST_OPTIONS__CERT_PATHS   | array of trusted SSL cert paths (optional, [details](https://vertx.io/docs/apidocs/io/vertx/core/net/PemTrustOptions.html))                  |
| OIDC_PEM_TRUST_OPTIONS__CERT_VALUES  | array of Base64-encoded trusted SSL cert values (optional, [details](https://vertx.io/docs/apidocs/io/vertx/core/net/PemTrustOptions.html)) |

<a id="config-api-groups"></a>
### API Groups

API Groups allow separating routing rule sets. You can define a set of rules and expose it on a domain and/or base-path.
An incoming request is initially matched against domain and base-path and then dispatched to appropriate set for
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
|:-------------------------|:---------------------------------------------------|
| _group.domains           | Host headers Edge matches the API group for        |
| _group.basePath          | base path Edge matches the API group at (optional) |

Note `_` (underscore) in `_rules` and `_group`.

[API Groups configuration details.](docs/api-groups.md)

<a id="config-service-discovery"></a>
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
|:-------------------|:----------------------------------------------------|
| targetService      | service-name of target nodes from service-discovery |

Edge calls nodes with `targetService` service-name using a round-robin load balancer.

Below you will find instructions on how to enable service discovery providers.

#### Consul service discovery

Add `sd-provider/consul` to `MODULES` environment variable, i.e. `MODULES=["sd-provider/consul"]`.

| Env variable          | Description                                         |
|:----------------------|:----------------------------------------------------|
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
        "root": "/v1"
      }
    }
  ]
}
```

| Attribute          | Description                          |
|:-------------------|:-------------------------------------|
| name               | service-name of target node          |
| location.host      | host of target node                  |
| location.port      | port of target node                  |
| location.ssl       | SSL of target node                   |
| location.root      | root path of target node (optional)  |

#### Self-registration in Consul

Edge node can register itself in Consul for service discovery.

Add `sd-registrar/consul` to `MODULES` environment variable, i.e. `MODULES=["sd-registrar/consul"]`.

| Env variable                     | Description                                                    |
|:---------------------------------|:---------------------------------------------------------------|
| CONSUL_HOST                      | host                                                           |
| CONSUL_POST                      | port (default 8500)                                            |
| CONSUL_SSL                       | SSL enabled (default false)                                    |
| CONSUL_ACL_TOKEN                 | ACL token (optional)                                           |
| CONSUL_DC                        | data center (optional)                                         |
| CONSUL_TIMEOUT                   | connection timeout (optional)                                  |
| REGISTER_SD_SERVICE_NAME         | Edge service name                                              |
| REGISTER_SD_HOST                 | host of Edge node                                              |
| REGISTER_SD_PORT                 | port of Edge node                                              |
| REGISTER_SD_SSL                  | ssl of Edge node (default false)                               |
| REGISTER_SD_HEALTHCHECK_HOST     | host of Edge health-check (default SELF_SD_SERVICE_NAME)       |
| REGISTER_SD_HEALTHCHECK_PORT     | port of Edge health-check (default SELF_SD_HEALTHCHECK_PORT)   |
| REGISTER_SD_HEALTHCHECK_PATH     | path of Edge health-check (default /alive)                     |
| REGISTER_SD_HEALTHCHECK_INTERVAL | health-check interval (default 3s)                             |
| REGISTER_SD_DEREGISTER_AFTER     | node de-register period when health-check fails (default 600s) |
| REGISTER_SD_TAGS                 | extra node tags (default [])                                   |

<a id="config-http-server"></a>
### HTTP server

Edge uses Vertx HTTP server implementation. Use environment variables to configure `io.vertx.core.http.HttpServerOptions`.

Environment variables map to `HttpServerOptions` ([see docs](https://vertx.io/docs/apidocs/io/vertx/core/http/HttpServerOptions.html)) attributes in following way:

* All variable names start with `HTTP_SERVER_` prefix,
* HttpServerOptions attribute name is capitalized and camel-case is replaced with underscore `_`.
* If an attribute has object value then it's sub-attribute env names are prefixed with `HTTP_SERVER_{parent-env-name}__` (note double underscore).

Examples:

| Name                                            | HttpServerOptions attribute                                                                                                                                |
|:------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| HTTP_SERVER_PORT                                | [port](https://vertx.io/docs/apidocs/io/vertx/core/http/HttpServerOptions.html#setPort-int-)                                                               |
| HTTP_SERVER_ACCEPT_BACKLOG                      | [acceptBacklog](https://vertx.io/docs/apidocs/io/vertx/core/http/HttpServerOptions.html#setAcceptBacklog-int-)                                            |
| HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_PATHS       | [pemTrustOptions.certPaths](https://vertx.io/docs/apidocs/io/vertx/core/http/HttpServerOptions.html#setPemTrustOptions-io.vertx.core.net.PemTrustOptions-) |

In order to set `HttpServerOptions` attribute with collection value use JSON syntax, e.g. `HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_PATHS=["/etc/ssl/cert.pem"]`.

<a id="config-http-clients"></a>
### HTTP clients

Edge uses Vertx implementation of HTTP clients. Use environment variables to configure default `io.vertx.core.http.HttpClientOptions`.

Environment variables map to `HttpClientOptions` ([see docs](https://vertx.io/docs/apidocs/io/vertx/core/http/HttpClientOptions.html)) attributes the same way they map to `HttpServerOptions`.


| Name                                            | HttpClientOptions attribute                                                                                                                                |
|:------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| HTTP_CLIENT_MAX_POOL_SIZE                       | [maxPoolSize](https://vertx.io/docs/apidocs/io/vertx/core/http/HttpClientOptions.html#setMaxPoolSize-int-)                                                 |
| HTTP_CLIENT_KEEP_ALIVE                          | [keepAlive](https://vertx.io/docs/apidocs/io/vertx/core/http/HttpClientOptions.html#setKeepAlive-boolean-)                                                 |
| HTTP_CLIENT_TRUST_ALL                           | [trustAll](https://vertx.io/docs/apidocs/io/vertx/core/http/HttpClientOptions.html#setTrustAll-boolean-)                                                   |


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

<a id="config-open-tracing"></a>
### Open tracing

Add `tracing/jaeger` to `MODULES` environment variable, i.e. `MODULES=["tracing/jaeger"]`.

| Env variable                      | Description                                    |
|:----------------------------------|:-----------------------------------------------|
| TRACING_SERVICE_NAME              | Edge name in Jaeger                            |
| JAEGER_AGENT_HOST                 | Jaeger agent host                              |
| JAEGER_AGENT_PORT                 | Jaeger agent port (optional)                   |
| JAEGER_SAMPLER_MANAGER_HOST_PORT  | Jaeger sampler host:port (optional)            |
| TRACING_FORMAT                    | tracing format - cloudentity, jaeger, zipkin   |

<a id="config-access-log"></a>
### Access log

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
|:----------------------|:----------------------------------------------------------------------------------------------------------|
| timestamp             | request time in ISO 8601 format                                                                           |
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

#### Authentication context and request headers in the access log

| Env variable                            | Description                                         | Example                                             |
|:----------------------------------------|:----------------------------------------------------|:----------------------------------------------------|
| ACCESS_LOG_SLF4J_DISABLED               | disable SLF4J access logging (default false)        | true                                                |
| ACCESS_LOG_AUTHN_CTX                    | authentication context set in access log (optional) | {"method":"authnMethod","user":"sub","uid":"email"} |
| ACCESS_LOG_REQUEST_HEADERS_ALL          | log all headers flag (default false)                | true                                                |
| ACCESS_LOG_REQUEST_HEADERS_WHITELIST    | log selected headers (optional)                     | ["Host","User-Agent"]                               |
| ACCESS_LOG_REQUEST_HEADERS_MASK_WHOLE   | mask whole logged header (optional)                 | ["Authorization"]                                   |
| ACCESS_LOG_REQUEST_HEADERS_MASK_PARTIAL | mask logged header partially (optional)             | ["Token"]                                           |

<a id="config-proxy-headers"></a>
### Proxy headers

Edge applies following request headers modification (unless disabled):

* Add `remote-address.host` to `X-Forwarded-For` headers
* Add `remote-address.protocol` to `X-Forwarded-Proto` headers
* If `Host` header is set then add it to `X-Forwarded-Host` headers
* If True Client IP header is missing then set it to first `X-Forwarded-For` value
* Set True Client IP header to upstream service

| Env variable                      | Description                                            |
|:----------------------------------|:-------------------------------------------------------|
| PROXY_HEADERS_ENABLED             | enable proxy headers (default true)                    |
| INPUT_TRUE_CLIENT_IP_HEADER       | True Client IP header name (default X-Real-IP)         |
| OUTPUT_TRUE_CLIENT_IP_HEADER      | Outgoing True Client IP header name (default X-Real-IP)|

<a id="config-performance"></a>
## Performance

We have put Edge Gateway under load to see how performant it is.

### Setup

* The test was run on a machine with i7-8550U CPU @ 1.80GHz
* `wrk` is used to generate load, a single test takes 30s and uses 10 threads
* target service is mocked with server responding to 140k req/sec with ~20 bytes response body

### Proxying request with no plugins

Edge Gateway proxies requests to mocked target service without applying any plugins.

With no target service delay and 30 connections:

| Requests/sec | Latency avg | Latency Stdev | Latency p90 | Latency p99 |
|:-------------|:------------|:--------------|:------------|:------------|
| 22692        | 1.42 ms     | 1.25 ms       | 2.34 ms     | 5.18 ms     |


With 50 ms target service delay and 200 connections:

| Requests/sec | Latency avg | Latency Stdev | Latency p90 | Latency p99 |
|:-------------|:------------|:--------------|:------------|:------------|
| 3787         | 2.69 ms     | 2.62 ms       | 5.70 ms     | 13.23 ms    |

NOTE: due to 50 ms delay the target service can't respond to more than 4000 requests/s.

### Proxying request with applying JWT-signing plugin

Edge Gateway signs each request with empty JWT with symmetric key, puts the signature in the header and proxies request to mocked target service.

With no target service delay and 30 connections:

| Requests/sec | Latency avg | Latency Stdev | Latency p90 | Latency p99 |
|:-------------|:------------|:--------------|:------------|:------------|
| 12415        | 2.65 ms     | 3.33 ms       | 3.62 ms     | 9.27 ms     |

With 50 ms target service delay and 200 connections:

| Requests/sec | Latency avg | Latency Stdev | Latency p90 | Latency p99 |
|:-------------|:------------|:--------------|:------------|:------------|
| 3233         | 10.41 ms    | 9.28 ms       | 24.01 ms    | 40.85 ms    |

NOTE: due to 50 ms delay the target service can't respond to more than 4000 requests/s.


[cloudentity-logo]: docs/logo-3x.png