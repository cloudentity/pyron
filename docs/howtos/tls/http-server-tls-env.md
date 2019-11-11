## Configure SSL/TLS for ingress traffic with private key in environment variable or file

In this how-to, we will use X.509 certificates to secure ingress communication using SSL/TLS.
We will store a TLS private key using environment variable or file and enable it in Pyron with its corresponding certificate.

In order to configure Pyron, set related environment variables in `envs` file.

* [Prerequisites](#pre)
* [Enable SSL/TLS](#enable)
* [Store private key](#key-env)
* [Store certificate](#cert-env)

> NOTE<br/>
> Read about [private key and certificate format](keycert-format.md).<br/>
> Read how to [configure mutual SSL/TLS](http-server-mtls-env.md).

<a id="pre"></a>
### Prerequisites

* You have a valid TLS private key and certificate.

<a id="enable"></a>
### Enable SSL/TLS

* Set `HTTP_SERVER_SSL` to `true`.
* Set `HTTP_SERVER_SNI` to `true` (optional, enables Server Name Indication).

<a id="key-env"></a>
### Store private key

Set `HTTP_SERVER_PEM_KEY_CERT_OPTIONS__KEY_VALUE` with Base64-encoded private key PEM block:

```
HTTP_SERVER_PEM_KEY_CERT_OPTIONS__KEY_VALUE=LS0tLS1CRUdJTiB...
```

or set path to private key file:

```
HTTP_SERVER_PEM_KEY_CERT_OPTIONS__KEY_PATH=/mykey.pem
```

> NOTE<br/>
> Storing private key in environment variable is not secure. [Use Vault](http-server-tls-vault.md) instead.

<a id="cert-env"></a>
### Store certificate

Set `HTTP_SERVER_PEM_KEY_CERT_OPTIONS__CERT_VALUE` with a Base64-encoded certificate PEM block:

```
HTTP_SERVER_PEM_KEY_CERT_OPTIONS__CERT_VALUE=IC0tLS0tQkVHSU4g...
```

or set path to certificate file:

```
HTTP_SERVER_PEM_KEY_CERT_OPTIONS__CERT_PATH=/mycert.pem
```