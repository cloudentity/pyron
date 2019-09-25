## Content

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
  * [Service discovery](#service-discovery)
    * Consul service discovery
    * Configuration-based service discovery
  * [HTTP server](#http-server)
  * [HTTP clients](#http-clients)
    * Default retries and timeout
    * Circuit breaker
  * [Open tracing](#open-tracing)

<a name="build"/>
## Build

Edge depends on github.com/cloudentity/vertx-tools. Clone it and build with `mvn install` command first.

<a name="build-standalone"/>
### Standalone

`mvn clean install -Pbuild-standalone`

<a name="build-docker"/>
### Docker

`mvn clean install -Pbuild-latest-docker`

<a name="run"/>
## Run

Configure routing rules in `rules.json` and environment variables in `envs` file if required.

<a name="run-standalone"/>
### Standalone

* `cd run/standalone`
* `*./run.sh`

<a name="run-docker"/>
### Docker

* `cd run/docker`
* `docker run --env-file envs -p 8080:8080 --name edge -v "$(pwd)"/configs:/configs -d docker.artifactory.syntegrity.com/edge:latest`

<a name="configure"/>
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

Above `meta-config.json` defines two configuration stores: `config.json` from classpath and `rules.json` from file system.

`config.json` defines minimal configuration required to run Edge. Routing rules are provided in `rules.json`.

<a name="routing"/>
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

`pathPattern` is regular expression extended with support of path-param placeholders, e.g. `/user/{id}`

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
        "dropPathPrefix": true // default
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

By default the prefix is dropped when calling target service. I.e. endpoint exposed at `POST /example/user` is proxied to `POST /user` in target service.
To preserve the prefix set `dropPathPrefix` to false.

#### Rewrite path

```json
{
  "endpoints": [
    {
      "method": "GET",
      "pathPattern": "/user/{id}",
      "rewritePath": "/entities/user/{id}"
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

By default Edge uses target host to set Host header value when calling target service. When `preserveHostHeader` is set to true then the Host header sent by the client is used instead.

<a name="plugins"/>
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
          "config": {
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
          "config": {
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

### HTTP server

Edge uses Vertx HTTP server implementation. Use environment variables to configure `io.vertx.core.http.HttpServerOptions`.

Environment variables map to `HttpServerOptions` attributes in following way:

* All variable names start with `HTTP_SERVER_` prefix,
* HttpServerOptions attribute name is capitalized and camel-case is replaced with underscore `_`.
* If an attribute has object value then it's sub-attribute env names are prefixed with `HTTP_SERVER_{parent-env-name}__` (note double underscore).

Examples:

| Name                                            | HttpServerOptions attribute |
|-------------------------------------------------|-----------------------------|
| HTTP_SERVER_PORT                                | port                        |
| HTTP_SERVER_ACCEPT_BACKLOG                      | acceptBacklog               |
| HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_PATHS       | pemTrustOptions.certPaths   |

In order to set `HttpServerOptions` attribute with collection value use JSON syntax, e.g. `HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_PATHS=["/etc/ssl/cert.pem"]`.

### HTTP clients

Edge uses Vertx implementation of HTTP clients. Use environment variables to configure default `io.vertx.core.http.HttpClientOptions`.

Environment variables map to `HttpClientOptions` attributes the same way they map to `HttpServerOptions`.

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

<a name="open-tracing"/>
### Open tracing

Add `tracing/jaeger` to `MODULES` environment variable, i.e. `MODULES=["tracing/jaeger"]`.

| Env variable                      | Description                                    |
|-----------------------------------|------------------------------------------------|
| TRACING_SERVICE_NAME              | Edge name in Jaeger                            |
| JAEGER_AGENT_HOST                 | Jaeger agent host                              |
| JAEGER_AGENT_PORT                 | Jaeger agent port (optional)                   |
| JAEGER_SAMPLER_MANAGER_HOST_PORT  | Jaeger sampler host:port (optional)            |
| TRACING_FORMAT                    | tracing format - cloudentity, jaeger, zipkin   |
