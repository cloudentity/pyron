package com.cloudentity.edge.plugin.impl.authn

import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.security.{KeyPair, KeyPairGenerator}

import com.cloudentity.tools.vertx.bus.ServiceClientFactory
import com.cloudentity.tools.vertx.test.{VertxDeployTest, VertxUnitTest}
import com.nimbusds.jose.jwk.{JWK, JWKSet, RSAKey}
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}
import scalaz.{-\/, \/, \/-}

class HttpOidcClientTest extends VertxUnitTest {

  var oidcService: ClientAndServer = _

  @Before
  def before(): Unit = {
    oidcService = startClientAndServer(9907)
  }

  @After
  def after(): Unit = {
    oidcService.stop()
  }

  @Test
  def getPublicKeys(ctx: TestContext): Unit = {
    mockOidcKeys(oidcKeysMockedBody)

    val client = ServiceClientFactory.make(vertx().eventBus(), classOf[OidcClient])

    val config: JsonObject = verticleConfig
    val verticle = new HttpOidcClient()

    VertxDeployTest.deployWithConfig(vertx(), verticle, config)
      .compose { _ => client.getPublicKeys() }
      .compose { keySet => assertKeySetEquals(keySet, ctx, 1) }
      .compose { _ => {
        ctx.async().complete()
        Future.succeededFuture[Void]()
      }}
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def getModifiedPublicKeys(ctx: TestContext): Unit = {
    mockOidcKeys(oidcKeysMockedBody)

    val client = ServiceClientFactory.make(vertx().eventBus(), classOf[OidcClient])

    val config: JsonObject = verticleConfig
    val verticle = new HttpOidcClient()

    VertxDeployTest.deployWithConfig(vertx(), verticle, config)
      .compose { _ => client.getPublicKeys() }
      .compose { keySet => assertKeySetEquals(keySet, ctx, 1) }
      .compose { _ => changeOidcKeys()}
      .compose { _ => client.getPublicKeys() }
      .compose { keySet => assertKeySetEquals(keySet, ctx, 0) }
      .compose { _ => {
        ctx.async().complete()
        Future.succeededFuture[Void]()
      }}
      .setHandler(ctx.asyncAssertSuccess())
  }

  def assertKeySetEquals(result: \/[OidcClientError, JWKSet], ctx: TestContext, expectedSize: Int): Future[Void] = {
    result match {
      case \/-(keysSet) => {
        ctx.assertNotNull(keysSet)
        ctx.assertEquals(expectedSize, keysSet.getKeys.size())
      }
      case -\/(ex) => {
        ctx.fail(s"Failed to get jwk key set: ${ex}")
      }
    }
    Future.succeededFuture[Void]()
  }

  def changeOidcKeys(): Future[Void] = {
    oidcService.reset()
    mockOidcKeys("{\"keys\":[]}")
    Thread.sleep(1000) // time to propagate
    Future.succeededFuture[Void]()
  }

  private def mockOidcKeys(body: String) = {
    oidcService.when(HttpRequest.request().withMethod("GET").withPath("/oauth/jwk"))
      .respond(HttpResponse.response(body).withStatusCode(200))
  }

  private def oidcKeysMockedBody = {
    val keyPair = generateRsaKeyPair
    val jwk = new JWKSet(generateRsaJwk(keyPair, "rsa1"))
    jwk.toString
  }

  def generateRsaKeyPair: KeyPair = {
    val gen = KeyPairGenerator.getInstance("RSA")
    gen.initialize(2048)
    gen.generateKeyPair
  }

  def generateRsaJwk(keyPair: KeyPair, kid: String): JWK = {
    new RSAKey.Builder(keyPair.getPublic.asInstanceOf[RSAPublicKey])
      .privateKey(keyPair.getPrivate.asInstanceOf[RSAPrivateKey])
      .keyID(kid).build
  }

  private def verticleConfig = {
    val config = new JsonObject
    val oidcServiceConf = new JsonObject
    oidcServiceConf.put("ssl", false)
    oidcServiceConf.put("host", "localhost")
    oidcServiceConf.put("path", "/oauth")
    oidcServiceConf.put("port", 9907)
    oidcServiceConf.put("timeout", 3000)
    oidcServiceConf.put("debug", false)
    oidcServiceConf.put("trustAll", false)
    oidcServiceConf.put("jwkEndpoint", "/jwk")
    oidcServiceConf.put("jwkReload", 1000)
    config.put("oidcService", oidcServiceConf)
    config
  }
}
