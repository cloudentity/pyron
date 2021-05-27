## Transform response cookie plugin

`transform-response-cookie` plugin allows setting of any attributes of cookies encoded in Set-Cookie, which are sent out through Pyron.
Changes, as specified in the plugin's conf, will be applied to the cookies matching the filter.
Filter is based on "name", "domain" and "path" attributes.
The name is mandatory, domain and path are optional.
If not set, they will match any value.
If explicitly set to null, they will match only cookies that don't contain this attribute.


Enable `transform-response-cookie` plugin by adding `plugin/transform-response-cookie` to `MODULES` environment variable.

#### Example configuration may look like so:

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
          "pathPattern": "/transform-some-cookies",
          "responsePlugins": [
            {
              "name": "transform-response-cookie",
              "conf": {
                "name": "originalCookieName",
                "domain": "optional.domain.com",
                "path": "/optional/path/",
                "body": {
                  "set": {
                    "name": "newCookieName",
                    "value": "new-cookie-value",
                    "domain": "new.cookie.com",
                    "path": "/new/path",
                    "maxAge": 123456,
                    "httpOnly": true,
                    "secure": true,
                    "sameSite": "Strict",
                    "wrap": true
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
On top of the "conf" we have the "name", "domain" and "path" filters.

Inside "set" object, we have fields corresponding to all cookie attributes.
Setting the field to specific value, will set that attribute on any cookie matching the filter.
If attribute exists on the cookie it will be updated, if it doesn't it will be added.
Setting some field within "set" explicitly to null, will purge this attribute on all matching cookies.
However, it will not unset name or value of the cookie, since these are strictly required attributes.
Multiple modifications can be defined at once and will all be applied.
