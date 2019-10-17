## How to read routing rules from Consul

So far the routing rules were read from `rules.json` file in `run/standalone` or `run/docker` folder.
In this how-to we will configure Pyron to read routing rules from Consul KV store by [consul-json](https://github.com/Cloudentity/vertx-tools#meta-custom-stores).

**Set content of `meta-config.json`:**

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
      "type": "consul-json",
      "format": "json",
      "config": {
        "host": "$env:CONSUL_HOST:string",
        "port": "$env:CONSUL_PORT:int:8500",
        "ssl": "$env:CONSUL_SSL:boolean:false",
        "aclToken": "$env:?CONSUL_ACL_TOKEN:string",
        "dc": "$env:?CONSUL_DC:string",
        "timeoutMs": "$env:?CONSUL_TIMEOUT:int",
        "path": "$env:CONSUL_RULES_STORE_PATH:string",
        "fallback": "$env:?CONSUL_STORE_FALLBACK_CONFIG:object"
      }
    }
  ]
}
```

**Set environment variables in `envs` file:**

| Name                          | Description                                             |
|:------------------------------|:--------------------------------------------------------|
| CONSUL_HOST                   | host                                                    |
| CONSUL_PORT                   | port (default 8500)                                     |
| CONSUL_SSL                    | SSL enabled (default false)                             |
| CONSUL_ACL_TOKEN              | ACL token (optional)                                    |
| CONSUL_DC                     | data center (optional)                                  |
| CONSUL_TIMEOUT                | connection timeout (optional)                           |
| CONSUL_RULES_STORE_PATH       | path in Consul KV where routing rules are stored        |
| CONSUL_STORE_FALLBACK_CONFIG  | fallback configuration if no value in Consul (optional) |

Now you can start Pyron and routing rules will be read from Consul. Similarly, you can move `system.json` configuration to Consul.