## Configure mutual SSL/TLS for ingress traffic with Vault or env variables

Pyron can be configured to require client authentication in SSL/TLS communication for ingress traffic.

* [Prerequisites](#pre)
* [Certificate format](#format)
* [Enable client authentication](#enable)
* [Store certificate in environment variable or file](#cert-env)

<a href="pre"></a>
### Prerequisites

* You have configured [server SSL/TLS](http-server-tls.md).
* You have a valid SSL certificate you want Pyron to trust.

### Certificate format

A certificate must be in X.509 format wrapped in a PEM block, for example:

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

<a href="enable"></a>
### Enable client authentication

* Set `HTTP_SERVER_CLIENT_AUTH` to `REQUEST` or `REQUIRED`.

<a href="cert-env"></a>
### Store certificate in environment variable or file

Configure one of:

| Env                                         | Description                                            | Example                                       |
|:------------------------------------------- |:-------------------------------------------------------|:----------------------------------------------|
| HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_VALUE   | Base64-encoded certificate                             | IC0tLS0tQkVHSU4g...                           |
| HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_VALUES  | an array containing Base64-encoded certificates        | ["IC0tLS0tQkVHSU4g...","IC0tLS0tQkVHSU4g..."] |
| HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_PATH    | path to certificate file                               | mycert1.pem                                   |
| HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_PATHS   | an array containing paths to certificate file          | ["/mycert1.pem","mycert2.pem"]                |
