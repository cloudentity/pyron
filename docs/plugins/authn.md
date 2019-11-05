## Authentication plugin

`authn` plugin performs authentication and optionally sets entities in the request authentication context.

Enable `authn` plugin by adding `plugin/authn` to `MODULES` environment variable.

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
  ]
}
```

Configuration attributes:

| Name               | Description                                                                     |
|:-------------------|:--------------------------------------------------------------------------------|
| `methods`          | list of enabled authentication methods                                          |
| `entities`         | required entities set in authentication context                                 |
| `tokenHeader`      | name of the HTTP header containing authentication token (default Authorization) |

### OAuth 2 with JWT access token

Enable OAuth 2 authentication method for JWT access tokens by adding `plugin/authn/oauth2` to `MODULES` environment variable.

This module enables `oauth2` authentication method with `jwt` entity provider. `jwt` provider puts all JWT claims in authentication context.

Token header sent by the client should have following format: `Bearer {access-token}`.

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
  ]
}
```

Configure:

| Env variable                                         | Description                                                                                                                                  |
|:-----------------------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------|
| PLUGIN_AUTHN_OAUTH2__SERVER_HOST                     | Authorization Server host                                                                                                                    |
| PLUGIN_AUTHN_OAUTH2__SERVER_PORT                     | Authorization Server port                                                                                                                    |
| PLUGIN_AUTHN_OAUTH2__SERVER_SSL                      | SSL enabled (default false)                                                                                                                  |
| PLUGIN_AUTHN_OAUTH2__JWK_ENDPOINT                    | public server JSON Web Key endpoint path                                                                                                     |
| PLUGIN_AUTHN_OAUTH2__TRUST_ALL                       | trust all Authorization Server SSL certificates (default false)                                                                             |
| PLUGIN_AUTHN_OAUTH2__PEM_TRUST_OPTIONS__CERT_PATHS   | array of trusted SSL cert paths (optional, [details](https://vertx.io/docs/apidocs/io/vertx/core/net/PemTrustOptions.html))                  |
| PLUGIN_AUTHN_OAUTH2__PEM_TRUST_OPTIONS__CERT_VALUES  | array of Base64-encoded trusted SSL cert values (optional, [details](https://vertx.io/docs/apidocs/io/vertx/core/net/PemTrustOptions.html))  |

### OAuth 2 with opaque token introspection

Enable OAuth 2 authentication method for opaque token introspection by adding `plugin/authn/oauth2-introspect` to `MODULES` environment variable.

This module enables `oauth2-introspect` authentication method with `jwt` entity provider. `jwt` provider puts in authentication context all claims returned in introspection response.

The method uses `Basic` authentication scheme with client id and secret to call introspection endpoint and expects a response with `application/json` content type.

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
          "pathPattern": "/user",
          "requestPlugins": [
            {
              "name": "authn",
              "conf": {
                "methods": ["oauth2-introspect"],
                "entities": ["jwt"]
              }
            }
          ]
        }
      ]
    }
  ]
}
```

Configure:

| Env variable                                                    | Description                                                                                                                                  |
|:----------------------------------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------|
| PLUGIN_AUTHN_OAUTH2_INTROSPECT__INTROSPECT_ENDPOINT             | introspection endpoint path                                                                                                                  |
| PLUGIN_AUTHN_OAUTH2_INTROSPECT__EXTRA_FORM_PARAMS               | extra form parameters sent in introspection request, e.g. `{"token_type_hint":"access_token"}`                                               |
| PLUGIN_AUTHN_OAUTH2_INTROSPECT__CLIENT_ID                       | client id used in basic authentication                                                                                                       |
| PLUGIN_AUTHN_OAUTH2_INTROSPECT__CLIENT_SECRET                   | client secret used in basic authentication                                                                                                   |
| PLUGIN_AUTHN_OAUTH2_INTROSPECT__TOKEN_HEADER                    | token header name (default `Authorization`)                                                                                                  |
| PLUGIN_AUTHN_OAUTH2_INTROSPECT__TOKEN_HEADER_REGEX              | token header value regex pattern (default `Bearer(.*)`)                                                                                      |
| PLUGIN_AUTHN_OAUTH2_INTROSPECT__SERVER_HOST                     | Authorization Server host                                                                                                                    |
| PLUGIN_AUTHN_OAUTH2_INTROSPECT__SERVER_PORT                     | Authorization Server port                                                                                                                    |
| PLUGIN_AUTHN_OAUTH2_INTROSPECT__SERVER_SSL                      | SSL enabled flag                                                                                                                             |
| PLUGIN_AUTHN_OAUTH2_INTROSPECT__MAX_POOL_SIZE                   | max HTTP connections pool size to introspection endpoint (default 5)                                                                         |
| PLUGIN_AUTHN_OAUTH2_INTROSPECT__TRUST_ALL                       | trust all Authorization Server SSL certificates (default false)                                                                              |
| PLUGIN_AUTHN_OAUTH2_INTROSPECT__PEM_TRUST_OPTIONS__CERT_PATHS   | array of trusted SSL cert paths (optional, [details](https://vertx.io/docs/apidocs/io/vertx/core/net/PemTrustOptions.html))                  |
| PLUGIN_AUTHN_OAUTH2_INTROSPECT__PEM_TRUST_OPTIONS__CERT_VALUES  | array of Base64-encoded trusted SSL cert values (optional, [details](https://vertx.io/docs/apidocs/io/vertx/core/net/PemTrustOptions.html))  |
