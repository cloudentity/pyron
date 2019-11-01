## Configure SSL/TLS for ingress traffic with Vault or env variables

Pyron can be configured to use Privacy-enhanced Electronic Email (PEM) private keys and its certificates in SSL/TLS communication with clients.

We recommend storing keys and certificates in Vault. However, you can also provide them using environment variables.

In order to enable SSL/TLS, set related environment variables in `envs` file.

> NOTE<br/>
> [Read](http-server-mtls.md) how to configure mutual SSL/TLS for ingress traffic

### Prerequisites

* You have a valid SSL certificate.

### Key/cert format

A key must contain a Base64-encoded private key in PKCS8 format wrapped in a PEM block, for example:

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

or contain a Base64-encoded private key in PKCS1 format wrapped in a PEM block, for example:

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

Likewise, a certificate must contain a Base64-encoded X.509 certificate wrapped in a PEM block, for example:

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
* Set `HTTP_SERVER_SNI` to `true`.

### Keys/certs from Vault

### Trusted certificates from Vault

Add `ssl/vault-keycerts` config store module in `meta-config.json`:

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
      "module": "ssl/vault-trust"
    }
  ]
}
```

Configure environment variables:

| Env                                                 | Description                                                                     |
|:----------------------------------------------------|:--------------------------------------------------------------------------------|
| CONFIG_STORE_SSL_VAULT_KEYCERTS__VAULT_HOST         | Vault host                                                                      |
| CONFIG_STORE_SSL_VAULT_KEYCERTS__VAULT_PORT         | Vault port                                                                      |
| CONFIG_STORE_SSL_VAULT_KEYCERTS__VAULT_SSL          | enabled flag (default false)                                                    |
| CONFIG_STORE_SSL_VAULT_KEYCERTS__ROOT_CA            | Base64-encoded root CA used for SSL communication with Vault (optional)         |
| CONFIG_STORE_SSL_VAULT_KEYCERTS__VAULT_PATH         | secrets Vault path with keycerts, relative to `/v1/secret/data`                 |
| CONFIG_STORE_SSL_VAULT_KEYCERTS__VAULT_SECRET_TOKEN | Vault token that allows access to `CONFIG_STORE_SSL_VAULT_KEYCERTS__VAULT_PATH` |

#### Upload domain keycerts

For each domain you want to configure SSL/TLS for create Vault secret at `/v1/secret/data/{CONFIG_STORE_SSL_VAULT_KEYCERTS__VAULT_PATH}/{domain_key}` path.
Each secret must contain `key` and `cert` attributes in Base64-encoded PEM format.

Example:

| Variable                                    | Value              |
|:--------------------------------------------|:-------------------|
| CONFIG_STORE_SSL_VAULT_KEYCERTS__VAULT_PATH | pyron/ssl-keycerts |
| domain_key                                  | cloudentity_com    |

```
curl -v -X POST localhost:8200/v1/secret/data/pyron/ssl-keycerts/cloudentity_com \
--data '{"data":{"key": "LS0tLS1CRUdJTiB...", "cert": "IC0tLS0tQkVHSU4g..."}}' \
-H "X-Vault-Token: {TOKEN}"
```

### Keys/certs from environment variables

#### Configure PEM keys

Set `HTTP_SERVER_PEM_KEY_CERT_OPTIONS__KEY_VALUES` with a JSON array containing Base64-encoded PEM block for each key.

Example:

```
HTTP_SERVER_PEM_KEY_CERT_OPTIONS__KEY_VALUES: ["LS0tLS1CRUdJTiB...", "LS0tLS1CRUdJTiB..."]
```

#### Configure PEM certs

Set `HTTP_SERVER_PEM_KEY_CERT_OPTIONS__CERT_VALUES` with a JSON array containing Base64-encoded PEM block for each certificate.

Example:

```
HTTP_SERVER_PEM_KEY_CERT_OPTIONS__CERT_VALUES: ["IC0tLS0tQkVHSU4g...", "IC0tLS0tQkVHSU4g..."]
```