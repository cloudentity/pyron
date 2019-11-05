## How to inject OAuth 2 subject into request

### Prerequisites

* Enable [transform-request](../plugins/transform-request.md) plugin
* Enable [authn](../plugins/authn.md) plugin with `oauth2` or `oauth2-introspect` authentication method

### Routing rule

Configure `authn` plugin to inject access token claims into authentication context:

```
...
"requestPlugins": [
  {
    "name": "authn",
    "conf": {
      "methods": ["oauth2"],
      "entities": ["jwt"]
    }
  },
  ...
]
...
```

 and then put `sub` claim  into `X-USER-ID` request header:

 ```
 ...
 "requestPlugins": [
   ...
   {
     "name": "transform-request",
     "conf": {
       "headers": {
         "set": {
           "X-USER-ID": "$authn.sub"
         }
       }
     }
   }
 ]
 ...
 ```

Full configuration:

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
          "method": "GET",
          "pathPattern": "/user",
          "requestPlugins": [
            {
              "name": "authn",
              "conf": {
                "methods": ["oauth2"],
                "entities": ["jwt"]
              }
            },
            {
              "name": "transform-request",
              "conf": {
                "headers": {
                  "set": {
                    "X-USER-ID": "$authn.sub"
                  }
                }
              }
            }
          ]
        }
      ]
    }
  ]
}
```

> NOTE<br/>
> Instead of `oauth2` authentication method you can use `oauth2-introspect` and read the subject from token introspection response body.