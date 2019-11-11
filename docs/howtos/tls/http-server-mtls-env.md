## Configure mutual SSL/TLS for ingress traffic with environment variable or file

In this how-to, we will use client CA certificate to secure ingress communication using mutual SSL/TLS.
We will store client CA certificate using environment variable or file and enable it in Pyron.

* [Prerequisites](#pre)
* [Certificate format](#format)
* [Enable client authentication](#enable)
* [Store certificate in environment variable or file](#cert-env)

<a id="pre"></a>
### Prerequisites

* You have configured SSL/TLS for ingress traffic using either [Vault secret]((http-server-tls-vault.md)) or [environment variable]((http-server-tls-env.md)).
* You have a valid SSL CA certificate you want Pyron to trust.

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

* Set `HTTP_SERVER_CLIENT_AUTH_REQUIRED` to `true`.

> NOTE<br/>
> If client authentication should be requested instead of required then set `HTTP_SERVER_CLIENT_AUTH` to `REQUIRED` (do not set `HTTP_SERVER_CLIENT_AUTH_REQUIRED`).

<a id="cert-env"></a>
### Store CA certificate in environment variable or file

Configure one of:

| Env                                         | Description                                            | Example                                       |
|:------------------------------------------- |:-------------------------------------------------------|:----------------------------------------------|
| HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_VALUES  | an array containing Base64-encoded certificates        | ["IC0tLS0tQkVHSU4g...","IC0tLS0tQkVHSU4g..."] |
| HTTP_SERVER_PEM_TRUST_OPTIONS__CERT_PATHS   | an array containing paths to certificate files         | ["/mycert1.pem","mycert2.pem"]                |
