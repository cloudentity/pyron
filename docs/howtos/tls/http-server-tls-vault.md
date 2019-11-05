## Configure SSL/TLS for ingress traffic with private key in Vault secret

In this how-to, we will use X.509 certificates to secure ingress communication using SSL/TLS.
We will store a TLS private key using Vault secret and enable it in Pyron with its corresponding certificate.

In order to configure Pyron, set related environment variables in `envs` file.

* [Prerequisites](#pre)
* [Enable SSL/TLS](#enable)
* [Store private key](#key-vault)
* [Store certificate](#cert-env)

> NOTE<br/>
> Read about [private key and certificate format](keycert-format.md).

<a id="pre"></a>
### Prerequisites

* You have a valid TLS private key and certificate.

<a id="enable"></a>
### Enable SSL/TLS

* Set `HTTP_SERVER_SSL` to `true`.
* Set `HTTP_SERVER_SNI` to `true` (optional, enables Server Name Indication).

<a id="key-vault"></a>
### Store private key in Vault

Add `tls/vault-secret-key` config store module in `meta-config.json`:

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
      "module": "tls/vault-secret-key"
    }
  ]
}
```

Configure environment variables:

| Env                                              | Description                                                                                       |
|:-------------------------------------------------|:--------------------------------------------------------------------------------------------------|
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__VAULT_HOST    | Vault host                                                                                        |
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__VAULT_PORT    | Vault port                                                                                        |
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__VAULT_SSL     | Vault SSL enabled flag (default false)                                                            |
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__VAULT_PATH    | secrets Vault path with private key                                                               |
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__VAULT_KEY     | secret key with private key value (default `value`)                                               |
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__TOKEN         | Vault authentication token (optional, see below for other authentication backends)                |

If you want to use different Vault authentication backend than `token`, then configure following environment variables:

| Env                                              | Description                                                                                                |
|:-------------------------------------------------|:-----------------------------------------------------------------------------------------------------------|
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__AUTH_BACKEND  | Vault auth backend: `token`, `approle`, `userpass` or `cert` (default token)                               |
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__CERTS         | array of Base64-encoded TLS certificates used by Vault, e.g. `["IC0t..."]` (optional, `cert` auth backend) |
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__ROLE_ID       | role id  (optional, `approle` auth backend)                                                                |
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__SECRET_ID     | secret id  (optional, `approle` auth backend)                                                              |
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__USERNAME      | username  (optional, `userpass` auth backend)                                                              |
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__PASSWORD      | password  (optional, `userpass` auth backend)                                                              |

#### Upload domain private key

Create a Vault secret at `/v1/{CONFIG_STORE_TLS_VAULT_SECRET_KEY__VAULT_PATH}` path and set the Base64-encoded private key at `value` key (or `CONFIG_STORE_TLS_VAULT_SECRET_KEY__VAULT_KEY`).

__Example:__

```
CONFIG_STORE_TLS_VAULT_SECRET_KEY__VAULT_PATH=secret/data/example_com
CONFIG_STORE_TLS_VAULT_SECRET_KEY__VAULT_KEY=value
```

```
curl -v -X POST localhost:8200/v1/secret/data/example_com \
--data '{"data":{"value": "LS0tLS1CRUdJTiB..."}}' \
-H "X-Vault-Token: {TOKEN}"
```

<a id="cert-env"></a>
### Store certificate in environment variable or file

Set `HTTP_SERVER_PEM_KEY_CERT_OPTIONS__CERT_VALUE` with a Base64-encoded certificate PEM block:

```
HTTP_SERVER_PEM_KEY_CERT_OPTIONS__CERT_VALUE=IC0tLS0tQkVHSU4g...
```

or set path to certificate file:

```
HTTP_SERVER_PEM_KEY_CERT_OPTIONS__CERT_PATH=/mycert.pem
```