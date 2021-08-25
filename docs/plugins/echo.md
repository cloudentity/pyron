## Echo plugin

`echo` plugin allows to echo a request without reaching a target service.
This plugin can be used for usecases like
- return custom signed jwt as headers
- echo back transformed request parameters without proxying to target service itself.

```json
{
  "default": {
    "targetHost": "virtual",
    "targetPort": 0,
    "pathPrefix": "/firebase/authn/jwt",
    "dropPrefix": true,
    "requestPlugins": [{
      "name": "headerToCtx",
      "conf": {
        "headers": [
          "X-Real-IP"
        ]
      }
    },
      {
        "name": "authn",
        "conf": {
          "methods": [
            "sso"
          ],
          "entities": [
            "token",
            "userUuid",
            "customerId"
          ]
        }
      }
    ]
  },
  "request": {
    "postFlow": {
      "plugins": [{
        "name": "outgoingCustomJwt",
        "conf": "$ref:custom-app-config"
      },
        {
          "name": "echo",
          "conf": {
            "headers": false,
            "uri": false,
            "queryParams": false,
            "method": false,
            "headersList": ["Authorization"],
            "body": false
          }
        }
      ]
    }
  },
  "endpoints": [{
    "method": "GET",
    "pathPattern": ".*"
  }]
}
```

Configuration attributes:

| Name            | Description                                                                                                                       |
|:----------------|:----------------------------------------------------------------------------------------------------------------------------------|
| headers         | return all headers in response . default is true                                                                                                    |
| uri             | return uri in response. default is false                                                    |
| queryParams     | return queryParams in response, default is false                                            |
| method          | return http method in response. default is false                                             |
| headersList     | selected headers to return                                                                    |
| body            | return request body. default is false                 |
                                                                                             |
### Example

In the above sample configuration, api sample response would be

```json
{
    "headers": {
        "Authorization": [
            "Bearer .."
        ]
    },
    "queryParams": "",
    "uri": "",
    "method": "",
    "body": {}
}

```
