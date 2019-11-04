## Configure SSL/TLS for ingress traffic with Vault or env variables

In this how-to, we will use X.509 certificates to secure ingress communication using SSL/TLS.
We will store a TLS private key as a Vault secret (or environment variable) and enable it in Pyron with its corresponding certificate.

In order to configure Pyron, set related environment variables in `envs` file.

> NOTE<br/>
> [Read](http-server-mtls.md) how to configure mutual SSL/TLS for ingress traffic.

### Prerequisites

* You have a valid TLS private key and certificate.

### Key/cert format

A private key must be a in PKCS8 format wrapped in a PEM block, for example:

PEM block:

```
-----BEGIN PRIVATE KEY-----
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDV6zPk5WqLwS0a
...
K5xBhtm1AhdnZjx5KfW3BecE
-----END PRIVATE KEY-----
```

Base64-encoded PEM block:

```
LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2Z...
```

or in PKCS1 format wrapped in a PEM block, for example:

PEM block:

```
-----BEGIN RSA PRIVATE KEY-----
MIIEowIBAAKCAQEAlO4gbHeFb/fmbUF/tOJfNPJumJUEqgzAzx8MBXv9Acyw9IRa
...
zJ14Yd+t2fsLYVs2H0gxaA4DW6neCzgY3eKpSU0EBHUCFSXp/1+/
-----END RSA PRIVATE KEY-----
```

Base64-encoded PEM block:

```
IC0tLS0tQkVHSU4gUlNBIFBSSVZBVEUgS0VZLS0tLS0K...
```

Likewise, a certificate must be in X.509 format wrapped in a PEM block, for example:

PEM block:

```
-----BEGIN CERTIFICATE-----
MIIDezCCAmOgAwIBAgIEZOI/3TANBgkqhkiG9w0BAQsFADBuMRAwDgYDVQQGEwdV
...
+tmLSvYS39O2nqIzzAUfztkYnUlZmB0l/mKkVqbGJA==
-----END CERTIFICATE-----
```

Base64-encoded PEM block:

```
IC0tLS0tQkVHSU4gQ0VSVElGSUNBVEUtLS0tLQogTUlJ...
```

### Enable SSL/TLS

* Set `HTTP_SERVER_SSL` to `true`.
* Set `HTTP_SERVER_SNI` to `true` (optional, enables Server Name Indication).

### Private key from Vault

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
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__TOKEN         | Vault authentication token                                                                        |

If you want to use different authentication backend than `token`, then configure following environment variables:

| Env                                              | Description                                                                                       |
|:-------------------------------------------------|:--------------------------------------------------------------------------------------------------|
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__AUTH_BACKEND  | Vault auth backend: `token`, `approle`, `userpass` or `cert` (default token)                      |
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__CERT          | Base64-encoded certificate used for TLS communication with Vault  (optional, `cert` auth backend) |
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__ROLE_ID       | role id  (optional, `approle` auth backend)                                                       |
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__SECRET_ID     | secret id  (optional, `approle` auth backend)                                                     |
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__USERNAME      | username  (optional, `userpass` auth backend)                                                     |
| CONFIG_STORE_TLS_VAULT_SECRET_KEY__PASSWORD      | password  (optional, `userpass` auth backend)                                                     |

#### Upload domain private key

Create a Vault secret at `/v1/{CONFIG_STORE_TLS_VAULT_SECRET_KEY__VAULT_PATH}` path and set the Base64-encoded private key at `value` (or `CONFIG_STORE_TLS_VAULT_SECRET_KEY__VAULT_KEY`) key.

Example:

```
CONFIG_STORE_TLS_VAULT_SECRET_KEY__VAULT_PATH=secret/data/example_com
CONFIG_STORE_TLS_VAULT_SECRET_KEY__VAULT_KEY=value
```

```
curl -v -X POST localhost:8200/v1/secret/data/example_com \
--data '{"data":{"value": "LS0tLS1CRUdJTiB..."}' \
-H "X-Vault-Token: {TOKEN}"
```

#### Set private key in environment variable (alternative)

Set `HTTP_SERVER_PEM_KEY_CERT_OPTIONS__KEY_VALUE` with Base64-encoded private key PEM block:

```
HTTP_SERVER_PEM_KEY_CERT_OPTIONS__KEY_VALUE=LS0tLS1CRUdJTiB...
```

or set path to private key file:

```
HTTP_SERVER_PEM_KEY_CERT_OPTIONS__KEY_PATH=/mykey.pem
```

### Set certificate in environment variable

Set `HTTP_SERVER_PEM_KEY_CERT_OPTIONS__CERT_VALUE` with a Base64-encoded certificate PEM block:

```
HTTP_SERVER_PEM_KEY_CERT_OPTIONS__CERT_VALUE=IC0tLS0tQkVHSU4g...
```

or set path to certificate file:

```
HTTP_SERVER_PEM_KEY_CERT_OPTIONS__CERT_PATH=/mycert.pem
```