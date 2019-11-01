## Configure mutual SSL/TLS for ingress traffic with Vault or env variables

Pyron can be configured to require client authentication in SSL/TLS communication for ingress traffic.

We recommend storing keys and certificates in Vault. However, you can also provide them using environment variables.

In order to enable SSL/TLS, set related environment variables in `envs` file.

### Prerequisites

* You have configured [server SSL/TLS](http-server-tls.md).
* You have a valid SSL certificate you want Pyron to trust.

### Certificate format

A certificate must contain a Base64-encoded X.509 certificate wrapped in a PEM block, for example:

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
IC0tLS0tQkVHSU4gQ0VSVElGSUNBVEUtLS0tLQogTUlJRGV6Q0NBbU9nQXdJQk...
```

### Enable client authentication

* Set `HTTP_SERVER_CLIENT_AUTH` to `REQUEST` or `REQUIRED`.

### Trusted certificates from Vault

Add `ssl/vault-trust` config store module in `meta-config.json`:

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

| Env                                              | Description                                                                   |
|:-------------------------------------------------|:------------------------------------------------------------------------------|
| CONFIG_STORE_SSL_VAULT_TRUST__VAULT_HOST         | Vault host                                                                    |
| CONFIG_STORE_SSL_VAULT_TRUST__VAULT_PORT         | Vault port                                                                    |
| CONFIG_STORE_SSL_VAULT_TRUST__VAULT_SSL          | enabled flag (default false)                                                  |
| CONFIG_STORE_SSL_VAULT_TRUST__ROOT_CA            | Base64-encoded root CA used for SSL communication with Vault (optional)       |
| CONFIG_STORE_SSL_VAULT_TRUST__VAULT_PATH         | secrets Vault path with trusted certificates, relative to `/v1/secret/data`   |
| CONFIG_STORE_SSL_VAULT_TRUST__VAULT_SECRET_TOKEN | Vault token that allows access to `CONFIG_STORE_SSL_VAULT_TRUST__VAULT_PATH`  |

#### Upload client certs

For each client you want to configure SSL/TLS for create Vault secret at `/v1/secret/data/{CONFIG_STORE_SSL_VAULT_TRUST__VAULT_PATH}/{client_key}` path.
Each secret must contain `cert` attribute in Base64-encoded PEM format.

Example:

| Variable                                 | Value           |
|:-----------------------------------------|:----------------|
| CONFIG_STORE_SSL_VAULT_TRUST__VAULT_PATH | pyron/ssl-trust |
| client_key                               | client_a        |

```
curl -v -X POST localhost:8200/v1/secret/data/pyron/ssl-trust/client_a \
--data '{"data":{"cert": "IC0tLS0tQkVHSU4g..."}}' \
-H "X-Vault-Token: {TOKEN}"
```

### Trusted certificates from environment variables

* Set `HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_VALUES` with a JSON array containing Base64-encoded PEM block for each certificate.

Example:

`HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_VALUES: ["IC0tLS0tQkVHSU4g...", "IC0tLS0tQkVHSU4g..."]`