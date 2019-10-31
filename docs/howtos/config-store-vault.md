## How to read secrets from Vault

In this how-to we will configure Pyron to read secrets from Vault with [vault-ext](https://github.com/Cloudentity/vertx-tools#meta-custom-stores) config store
and [reference](https://github.com/Cloudentity/vertx-tools#config-references) them in routing rules configuration.

In [Scala](plugin-dev-scala.md)/[Java](plugin-dev-java.md) plugin development guide we implemented
a sample plugin that verifies API key sent by the client against the one configured in a routing rule.
We stored the API key value in configuration as plain-text. However, in production we don't want to do that.

Instead, we will read the API keys from Vault secrets store and reference them in routing rule config.

> NOTE<br/>
> Pyron stores the secrets in-memory, i.e. they are not printed in log messages.

`vault-ext` is a wrapper around Vertx [vault](https://vertx.io/docs/vertx-config/java/#_vault_config_store) config store.
`vault` supports several authentication method. In this how-to we use token-based authentication.

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
      "type": "file",
      "format": "json",
      "config": {
        "path": "rules.json"
      }
    },
    {
      "type": "vault-ext",
      "format": "json",
      "config": {
        "host": "$env:VAULT_HOST:string",
        "port": "$env:VAULT_PORT:int:8200",
        "ssl": "$env:VAULT_SSL:boolean:false",
        "token": "$env:VAULT_TOKEN:string",
        "path": "$env:VAULT_SECRETS_PATH:string",
        "ext": {
          "outputPath": "secrets",
          "maskSecrets": true,
          "cache": "$env:?VAULT_SECRETS_CACHE:boolean"
        }
      }
    }
  ]
}
```

`ext.outputPath` value defines the root path of reference we will use. For example secret with key `example` can be referenced as `secrets.example`.

> NOTE<br/>
> Routing rules are read from `rules.json` file in `run/standalone` or `run/docker` folder.

**Set environment variables in `envs` file:**

| Name                          | Description                                               |
|:------------------------------|:----------------------------------------------------------|
| VAULT_HOST                    | host                                                      |
| VAULT_POST                    | port (default 8200)                                       |
| VAULT_SSL                     | SSL enabled (default false)                               |
| VAULT_TOKEN                   | token                                                     |
| VAULT_SECRETS_PATH            | secrets path                                              |
| VAULT_SECRETS_CACHE           | set to true if using one-time Vault token (default false) |

Run Pyron with updated environment variables and `meta-config.json`.

**Reference secrets in routing rules config**

The snippet below configures a routing rule with secret reference:

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
              "name": "sample-verify-apikey",
              "conf": {
                "apiKey": "$ref:secrets.example"
              }
            }
          ]
        }
      ]
    }
  ]
}
```

**Example:**

If the secret API key was set with `vault kv put secret/api-keys example=abc` command then:

* `VAULT_SECRETS_PATH` environment variable should be set to `secret/data/api-keys`
* `apiKey` value in `sample-verify-apikey` plugin config is resolved to `abc`
