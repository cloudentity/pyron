## rate-limit plugin

`rate-limit` plugin restricts maximum number of requests per entity (client or resource) in time periods.

```json
{
  "rules": [
    {
      "default": {
        "targetHost": "example.com",
        "targetPort": 80
      },
      "endpoints": [
        {
          "method": "POST",
          "pathPattern": "/calculate",
          "requestPlugins": [
            {
              "name": "rate-limit",
              "conf": {
                "counterName": "calculate",
                "identifier": "$headers.API-KEY",
                "perHour": 1000,
                "perSecond": 10
              }
            }
          ]
        }
      ]
    }
  ]
}
```

Configuration attributes:

| Name            | Description                                    |
|:----------------|:-----------------------------------------------|
| counterName     | counter name                                   |
| identifier      | limited entity identifier (client or resource) |
| perHour         | max number of calls per entity in 1 hour       |
| perMinute       | max number of calls per entity in 1 minute     |
| perSecond       | max number of calls per entity in 1 second     |

### Counter name

`rate-limit` plugin uses counters to aggregate requests. By defining `counterName` in the configuration, all the requests to the rule endpoint
will be aggregated by that counter. Counters can be shared between endpoints. The plugin can be used to restrict access to a group of endpoints exposing the same functionality.

Example:
We have 2 methods of ingesting logs: via mobile or web. We want to limit the rate of logging requests (regardless method) to 10 per second.
The client identifies itself with a key sent in `APP-KEY` header.

```json
{
  "rules": [
    {
      "default": {
        "targetHost": "example.com",
        "targetPort": 80
      },
      "endpoints": [
        {
          "method": "POST",
          "pathPattern": "/log/mobile",
          "requestPlugins": [
            {
              "name": "rate-limit",
              "conf": {
                "counterName": "log",
                "identifier": "$headers.APP-KEY",
                "perSecond": 10
              }
            }
          ]
        },
        {
          "method": "POST",
          "pathPattern": "/log/web",
          "requestPlugins": [
            {
              "name": "rate-limit",
              "conf": {
                "counterName": "log",
                "identifier": "$headers.APP-KEY",
                "perSecond": 10
              }
            }
          ]
        }
      ]
    }
  ]
}
```

`/log/mobile` and `/log/web` share the same `log` counter. Calls to either of those endpoints sum together.
If the client exceeds the period limit then Pyron responds with 429 status code and aborts call to target service.

### Identifier

Counters aggregates the calls against identifier. It might be the identity of the client, target resource id, header value, JSON body attribute, IP of the client, etc.
If the number of requests with the same identifier exceeds the period limit then the counter raises an error and Pyron responds with 429 status code.

Supported identifiers:

| Identifier      | Example             | Description                                                 |
|:----------------|:--------------------|-------------------------------------------------------------|
| body            | $body.username      | JSON body attribute                                         |
| headers         | $headers.API-KEY    | header value                                                |
| authn           | $authn.sub          | authentication context, e.g. JWT OAuth access token payload |
| pathParams      | $pathParams.userId  | endpoint path parameter                                     |

### Period limits

Counter aggregates requests in period buckets - `perSecond`, `perMinute`, `perHour`.
The period buckets are cleared every second/minute/hour respectively starting at `rate-limit` plugin startup.
Let's suppose `perHour` limit is set to 1000 and the plugin started at 14:20.
Then the client has 1000 requests available between 14:20 - 15:20, next 1000 requests between 15:20 - 16:20, etc.

### How-tos

#### Limit per API key

Use `headers` to configure the API key header name as identifier.

```json
{
  "name": "rate-limit",
  "conf": {
    "counterName": "some-counter",
    "identifier": "$headers.X-API-KEY",
    "perSecond": 10
  }
}
```

#### Limit per client IP

Use `headers` to configure the true client IP as identifier.

> NOTE<br/>
> Pyron reads client IP and puts it into proxy headers ([see details](../#config-proxy-headers)).

```json
{
  "name": "rate-limit",
  "conf": {
    "counterName": "some-counter",
    "identifier": "$headers.X-Real-IP",
    "perSecond": 10
  }
}
```

#### Limit per `sub` from OAuth access token

> NOTE</br>
> You need [`authn`](authn.md) plugin with `oauth2` or `oauth2-introspect` authentication method deployed.

First apply `authn` plugin that puts content of OAuth2 JWT access token payload into authentication context and
then use access token `sub` as rate-limit identifier.

```json
{
  "rules": [
    {
      "default": {
        "targetHost": "example.com",
        "targetPort": 80
      },
      "endpoints": [
        {
          "method": "POST",
          "pathPattern": "/user/{userId}",
          "requestPlugins": [
            {
              "name": "authn",
              "conf": {
                "methods": "oauth2",
                "entities": "jwt"
              }
            },
            {
              "name": "rate-limit",
              "conf": {
                "counterName": "some-counter",
                "identifier": "$authn.sub",
                "perSecond": 10
              }
            }
          ]
        }
      ]
    }
  ]
}
```

#### Limit per resource

Use `pathParams`, `body` or `headers` to read resource identifier.

```json
{
  "rules": [
    {
      "default": {
        "targetHost": "example.com",
        "targetPort": 80
      },
      "endpoints": [
        {
          "method": "POST",
          "pathPattern": "/user/{userId}",
          "requestPlugins": [
            {
              "name": "rate-limit",
              "conf": {
                "counterName": "some-counter",
                "identifier": "$pathParams.userId",
                "perSecond": 10
              }
            }
          ]
        }
      ]
    }
  ]
}
```