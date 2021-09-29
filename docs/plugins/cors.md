## CORS plugin

`cors` plugin adds support for Cross-Origin Resource Sharing.

Enable `cors` plugin by adding `plugin/cors` to `MODULES` environment variable.

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
          "pathPattern": "/user/{id}",
          "requestPlugins": [
            {
              "name": "cors",
              "conf": {
                "allowCredentials": true,
                "allowedHttpHeaders": ["*"],
                "allowedHttpMethods": ["*"],
                "allowedOrigins": ["*"],
                "preflightMaxAgeInSeconds": 600
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

| Name                     | Description                                                                                                                                                                                                                                                  |
|:-------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| allowCredentials         | if true then `Access-Control-Allow-Credentials` preflight response header set to true, boolean flag, default `true`                                                                                                                                           |
| allowedHttpHeaders       | `Access-Control-Allow-Headers` preflight response header value is set to string of comma-separated values from `allowedHttpHeaders`, default `["*"]`                                                                                                          |
| allowedHttpMethods       | `Access-Control-Allow-Methods` preflight response header value is set to string of comma-separated values from `allowedHttpMethods`, default `["*"]`                                                                                                          |
| allowedOrigins           | if `allowedOrigins` is set to `*` or one of its values matches request origin then `Access-Control-Allow-Origin` preflight response header is set to request origin, otherwise set to string of comma-separated values from `allowedOrigins`, default `["*"]`. <br><br> Note that you need to include the full URL, including the protocol (`["https://domain-a.com, https://domain-b.com"]`, NOT `["domain-a.com, domain-b.com"]`). |
| preflightMaxAgeInSeconds | value of `Access-Control-Max-Age` preflight response header, default `600`                                                                                                                                                                                    |

Please refer to https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS for details regarding Access-Control headers.

Set default configuration attributes in environment variables:

| Env variable                              | Description                                  |
|:------------------------------------------|:---------------------------------------------|
| PLUGIN_CORS__ALLOW_CREDENTIALS            | default value of `allowCredentials`          |
| PLUGIN_CORS__ALLOWED_HTTP_HEADERS         | default value of `allowedHttpHeaders`        |
| PLUGIN_CORS__ALLOWED_HTTP_METHODS         | default value of `allowedHttpMethods`        |
| PLUGIN_CORS__ALLOWED_ORIGINS              | default value of `allowedOrigins`            |
| PLUGIN_CORS__PREFLIGHT_MAX_AGE_IN_SECONDS | default value of `preflightMaxAgeInSeconds`  |
