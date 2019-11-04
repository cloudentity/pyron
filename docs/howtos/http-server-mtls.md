## Configure mutual SSL/TLS for ingress traffic with Vault or env variables

Pyron can be configured to require client authentication in SSL/TLS communication for ingress traffic.

### Prerequisites

* You have configured [server SSL/TLS](http-server-tls.md).
* You have a valid SSL certificate you want Pyron to trust.

### Certificate format

A certificate must be a X.509 certificate wrapped in a PEM block, for example:

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

### Trusted certificates from environment variables or file

Configure one of:

| Env                                         | Description                                            | Example                                       |
|:------------------------------------------- |:-------------------------------------------------------|:----------------------------------------------|
| HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_VALUE   | Base64-encoded certificate                             | IC0tLS0tQkVHSU4g...                           |
| HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_VALUES  | a JSON array containing Base64-encoded certificates    | ["IC0tLS0tQkVHSU4g...","IC0tLS0tQkVHSU4g..."] |
| HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_PATH    | path to certificate file                               | mycert1.pem                                   |
| HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_PATHS   | a JSON array containing path to certificate file       | ["/mycert1.pem","mycert2.pem"]                |
