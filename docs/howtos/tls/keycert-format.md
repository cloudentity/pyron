## Key/cert format

Pyron uses X.509 certificates schema for SSL/TLS communication.

### Private key format

A private key must be in PKCS8 format wrapped in a PEM block, for example:

_PEM block:_
```
-----BEGIN PRIVATE KEY-----
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDV6zPk5WqLwS0a
...
K5xBhtm1AhdnZjx5KfW3BecE
-----END PRIVATE KEY-----
```

_Base64-encoded PEM block:_
```
LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2Z...
```

or in PKCS1 format wrapped in a PEM block, for example:

_PEM block:_
```
-----BEGIN RSA PRIVATE KEY-----
MIIEowIBAAKCAQEAlO4gbHeFb/fmbUF/tOJfNPJumJUEqgzAzx8MBXv9Acyw9IRa
...
zJ14Yd+t2fsLYVs2H0gxaA4DW6neCzgY3eKpSU0EBHUCFSXp/1+/
-----END RSA PRIVATE KEY-----
```

_Base64-encoded PEM block:_
```
IC0tLS0tQkVHSU4gUlNBIFBSSVZBVEUgS0VZLS0tLS0K...
```

### Certificate format

Likewise, a certificate must be wrapped in a PEM block, for example:

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
IC0tLS0tQkVHSU4gQ0VSVElGSUNBVEUtLS0tLQogTUlJ...
```