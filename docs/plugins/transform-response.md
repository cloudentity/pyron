## Response transformation plugin

`transform-response` plugin can be used to modify headers and JSON body on the response.

For modifying outgoing cookies encoded in `Set-Cookie` headers, please use [`transform-response-cookie` plugin](transform-response-cookie.md).

Enable `transform-response` plugin by adding `plugin/transform-response` to `MODULES` environment variable.
This plugin works in a very similar way to the `transform-request` plugin, except that modifications are applied to the response
and you can reference response attribute.

Additional references with sub-items:
* `resp.body`
* `resp.headers`

Additional references without sub-items:
* `resp.status`

Additional transformation:
* `status` - optional attribute to set http response to the configured value 

Other references are still accessible, and will resolve against the original request, the same way as for `transform-request` plugin.

Use "transform-response" for plugin name, and put the configuration inside `responsePlugins` array.
Other than that, all the usage examples will be the same as those already provided in [`transform-request` plugin's documentation](transform-request.md).

Example usage:

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
          "pathPattern": "/user/{id}",
          "rewritePath": "/user",
          "responsePlugins": [
            {
              "name": "transform-response",
              "conf": {
                "headers": {
                  "set": {
                    "X-USER-ID": "$resp.headers.userUUid"
                  }
                },
                "body": {
                  "set": {
                    "withdraw.allowDebit": true
                  }
                },
                "status": 200
              }
            }
          ]
        }
      ]
    }
  ]
}
```
