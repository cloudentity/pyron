## Configure mutual SSL/TLS for ingress traffic with Vault or env variables

Pyron can be configured to require client authentication in SSL/TLS communication for ingress traffic.

* [Prerequisites](#pre)
* [Certificate format](#format)
* [Enable client authentication](#enable)
* [Store certificate in environment variable or file](#cert-env)

<a id="pre"></a>
### Prerequisites

* You have configured SSL/TLS for ingress traffic using either [Vault secret]((http-server-tls-vault.md)) or [environment variable]((http-server-tls-env.md)).
* You have a valid SSL certificate you want Pyron to trust.

<a id="format"></a>
### Certificate format

A certificate must be in X.509 format wrapped in a PEM block, for example:

_PEM block:_
```
-----BEGIN CERTIFICATE-----
MIIDezCCAmOgAwIBAgIEZOI/3TANBgkqhkiG9w0BAQsFADBuMRAwDgYDVQQGEwdV
...
+tmLSvYS39O2nqIzzAUfztkYnUlZmB0l/mKkVqbGJA==
-----END CERTIFICATE-----
```

_Base64-encoded PEM block:_
```
IC0tLS0tQkVHSU4gQ0VSVElGSUNBVEUtLS0tLQogTUlJRGV6Q0NBbU9nQXdJQk...
```

<a id="enable"></a>
### Enable client authentication

* Set `HTTP_SERVER_CLIENT_AUTH` to `REQUEST` or `REQUIRED`.

<a id="cert-env"></a>
### Store certificate in environment variable or file

Configure one of:

| Env                                         | Description                                            | Example                                       |
|:------------------------------------------- |:-------------------------------------------------------|:----------------------------------------------|
| HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_VALUE   | Base64-encoded certificate                             | IC0tLS0tQkVHSU4g...                           |
| HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_VALUES  | an array containing Base64-encoded certificates        | ["IC0tLS0tQkVHSU4g...","IC0tLS0tQkVHSU4g..."] |
| HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_PATH    | path to certificate file                               | mycert1.pem                                   |
| HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_PATHS   | an array containing paths to certificate file          | ["/mycert1.pem","mycert2.pem"]                |
