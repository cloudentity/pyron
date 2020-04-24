## ACP authorizer plugin

`acp-authz` plugin sends request data to ACP authorizer to validate policy configured in ACP.

Enable `acp-authz` plugin by adding `plugin/acp-authz` to `MODULES` environment variable.

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
              "name": "acp-authz"
            }
          ]
        }
      ]
    }
  ]
}
```

Configure access to ACP authorizer using environment variables:

| Env variable                    | Description                              |
|:--------------------------------|:-----------------------------------------|
| PLUGIN_ACP_AUTHZ__HOST          | ACP authorizer host (default localhost)  |
| PLUGIN_ACP_AUTHZ__PORT          | ACP authorizer port (default 8080)       |
| PLUGIN_ACP_AUTHZ__SSL           | ACP authorizer SSL flag (default false)  |
| PLUGIN_ACP_AUTHZ__MAX_POOL_SIZE | max HTTP client pool size (default 20)   |