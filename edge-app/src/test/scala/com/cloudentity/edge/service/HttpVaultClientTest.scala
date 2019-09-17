package com.cloudentity.edge.service

import java.security.cert.X509Certificate

import com.cloudentity.edge.util.{JwtUtils, MockUtils}
import com.cloudentity.tools.vertx.bus.ServiceClientFactory
import com.cloudentity.tools.vertx.test.{VertxDeployTest, VertxUnitTest}
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}

import scalaz.{-\/, \/, \/-}

class HttpVaultClientTest extends VertxUnitTest with JwtUtils {

  var vaultService: ClientAndServer = _
  var client: VaultClient = _
  val config: JsonObject = verticleConfig
  val verticle = new HttpVaultClient()

  @Before
  def before(): Unit = {
    vaultService = startClientAndServer(9187)
    client = ServiceClientFactory.make(vertx().eventBus(), classOf[VaultClient])
  }

  @After
  def after(): Unit = {
    vaultService.stop()
  }

  @Test
  def getExistingPublicKey(ctx: TestContext): Unit = {
    val serial = "2a-45-f3-bd-2f-d3-f4-b5-b5-3d-1c-5f-65-cb-7d-33-07-6f-c9-fd"
    mockVaultServiceResponse(serial, vaultKeyMockedBody)

    VertxDeployTest.deployWithConfig(vertx(), verticle, config)
      .compose { _ => client.getPublicKey(serial) }
      .compose { result => assertKeyReturned(result, ctx) }
      .compose { _ => {
        ctx.async().complete()
        Future.succeededFuture[Void]()
      }}
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def getProperExceptionIfPublicKeyNotExists(ctx: TestContext): Unit = {
    val serial = "notExisting"
    mockVaultServicePublicKeyNotExistsResponse(serial)

    VertxDeployTest.deployWithConfig(vertx(), verticle, config)
      .compose { _ => client.getPublicKey(serial) }
      .compose { result => assertKeyNotExists(result, ctx) }
      .compose { _ => {
        ctx.async().complete()
        Future.succeededFuture[Void]()
      }}
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def exceptionFromVaultShouldBeHandledCorrectly(ctx: TestContext): Unit = {
    vaultService.stop()

    VertxDeployTest.deployWithConfig(vertx(), verticle, config)
      .compose { _ => client.getPublicKey("any") }
      .compose { result => assertExceptionThrownWhenServiceIsDown(result, ctx) }
      .compose { _ => {
        ctx.async().complete()
        Future.succeededFuture[Void]()
      }}
      .setHandler(ctx.asyncAssertSuccess())
  }

  def assertKeyReturned(result: \/[VaultClientError, Option[X509Certificate]], ctx: TestContext): Future[Void] = {
    result match {
      case \/-(Some(key)) => ctx.assertEquals("CN=myvault.com", key.getIssuerDN.getName)
      case \/-(None)      => ctx.fail(s"Failed to found public key")
      case -\/(ex)        => ctx.fail(s"Failed to get public key: ${ex}")
    }
    Future.succeededFuture[Void]()
  }

  def assertKeyNotExists(result: \/[VaultClientError, Option[X509Certificate]], ctx: TestContext): Future[Void] = {
    result match {
      case \/-(Some(_)) => ctx.fail(s"Public key should not exists")
      case \/-(None)      => {}
      case -\/(ex)        => ctx.fail(s"Failed to get public key: ${ex}")
    }
    Future.succeededFuture[Void]()
  }

  def assertExceptionThrownWhenServiceIsDown(result: \/[VaultClientError, Option[X509Certificate]], ctx: TestContext): Future[Void] = {
    result match {
      case \/-(Some(_))   => ctx.fail(s"Exception should be thrown")
      case \/-(None)      => ctx.fail(s"Exception should be thrown")
      case -\/(ex)        => ctx.assertTrue(ex.isInstanceOf[VaultClientHttpError])
    }
    Future.succeededFuture[Void]()
  }

  private def mockVaultServiceResponse(serial: String, body: String) = {
    vaultService.when(HttpRequest.request().withMethod("GET").withPath(s"/v1/pki/cert/${serial}"))
      .respond(HttpResponse.response(body).withStatusCode(200))
  }

  private def mockVaultServicePublicKeyNotExistsResponse(serial: String) = {
    vaultService.when(HttpRequest.request().withMethod("GET").withPath(s"/v1/pki/cert/${serial}"))
      .respond(HttpResponse.response("{\"errors\":[\"unsupported path\"]}").withStatusCode(404))
  }

  val vaultKeyMockedBody = """{"request_id":"7cabe2e1-86a4-6c53-af62-7f2b9611dba1","lease_id":"","renewable":false,"lease_duration":0,"data":{"certificate":"-----BEGIN CERTIFICATE-----\nMIID4zCCAsugAwIBAgIUKkXzvS/T9LW1PRxfZct9Mwdvyf0wDQYJKoZIhvcNAQEL\nBQAwFjEUMBIGA1UEAxMLbXl2YXVsdC5jb20wHhcNMTcxMjA3MjMwNTMxWhcNMTcx\nMjE0MjMwNjAxWjAsMSowKAYDVQQDEyExLmV4YW1wbGUtc2VydmljZS5jbG91ZGVu\ndGl0eS5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCq4IGvu2gb\nF4dgI21GZYAdI/3djQ+fa5k6F2rmeWA6E15l9lV9sqKLSA/Q6cZZFZdVjjTcOlwD\njDND3Td2mKvIfd8mt8B83TiWuaMSyK39yzrRmpeAvqkeXmp9wOZcAJreBWKPG6+i\n1f9ONZOHIKtN6XNxHWPtb8MBg42O/7WHBFLEXtAMH2RBeVxWiJ97C+m15i1bNAtG\nEtYC3OpqA8pw+QeIYqVJowUqU4kSVO2QR2i+NM5+9Z7MDfuSPnctpjrinfRguW3v\nMI8Xl1uPHx8G+CFqnWFx0JVx4IkrSUi17C5UDXHBJdbyxk8/BJxLQ8szDEGYUJpV\nZVIA7i6kHVM5AgMBAAGjggERMIIBDTAOBgNVHQ8BAf8EBAMCA6gwHQYDVR0lBBYw\nFAYIKwYBBQUHAwEGCCsGAQUFBwMCMB0GA1UdDgQWBBRjfeJS410nv0lKT5QbAu1U\nkXRFDzAfBgNVHSMEGDAWgBR5gHw4zkE1PvfKFjBTeIY+rgQNTDA7BggrBgEFBQcB\nAQQvMC0wKwYIKwYBBQUHMAKGH2h0dHA6Ly8xMjcuMC4wLjE6ODIwMC92MS9wa2kv\nY2EwLAYDVR0RBCUwI4IhMS5leGFtcGxlLXNlcnZpY2UuY2xvdWRlbnRpdHkuY29t\nMDEGA1UdHwQqMCgwJqAkoCKGIGh0dHA6Ly8xMjcuMC4wLjE6ODIwMC92MS9wa2kv\nY3JsMA0GCSqGSIb3DQEBCwUAA4IBAQAuOk/Hg28oHxWUEVswXeYOr+fCzqQbxG9j\nUEPgjyAldFRHSPgYbhSTmb6Q5/PwE0R7YoknwbVWwC/s6V/y6AVxscMdkDTBbwqx\nrQ197D5FcLL2qlrB4Zmr6vFiq0EZ6FKFGf/JqGYBHeLGaK6zQ8hruXd5CmslqQsM\niKpiQeSfLD2Z9/d6lucmp2zExM94339FuHVRkwoT4nSQ3J7mDIWF+JoAuSKQavY/\nTstYRhe+2LiTBuOmN6qeci1N7N+14GCG4bXlw+kLUmuhb2pRdfgnxGGlthTVROmA\nFQ5DYjpJ5lJBod1asNUfJvnW2srjxgLiQrnT/iVUp5IMqKAKvtyZ\n-----END CERTIFICATE-----\n","revocation_time":0},"wrap_info":null,"warnings":null,"auth":null}"""

  private def verticleConfig = {
    val config = new JsonObject
    val oidcServiceConf = new JsonObject
    oidcServiceConf.put("ssl", false)
    oidcServiceConf.put("host", "localhost")
    oidcServiceConf.put("path", "/v1")
    oidcServiceConf.put("port", 9187)
    oidcServiceConf.put("timeout", 3000)
    oidcServiceConf.put("debug", false)
    oidcServiceConf.put("trustAll", false)
    oidcServiceConf.put("publicKeyEndpoint", "/pki/cert")
    config.put("vaultService", oidcServiceConf)
    config
  }
}
