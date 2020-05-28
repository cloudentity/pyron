## ACP authorizer plugin

`acp-authz` plugin sends request data to ACP authorizer to validate policy configured in ACP.

> Learn about [ACP](https://cloudentity.com/authorization-control/).

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

> NOTE<br/>
> Pyron sends API groups to ACP to allow configuration of authorization policies.<br/>
> If you don't define rules in an API group (as in example above) then Pyron puts rules into `default` API group.<br/>
> Learn more about [API Groups configuration](../api-groups.md).

Configure access to ACP authorizer using environment variables:

| Env variable                    | Description                              |
|:--------------------------------|:-----------------------------------------|
| PLUGIN_ACP_AUTHZ__HOST          | ACP authorizer host (default localhost)  |
| PLUGIN_ACP_AUTHZ__PORT          | ACP authorizer port (default 8080)       |
| PLUGIN_ACP_AUTHZ__SSL           | ACP authorizer SSL flag (default false)  |
| PLUGIN_ACP_AUTHZ__MAX_POOL_SIZE | max HTTP client pool size (default 20)   |