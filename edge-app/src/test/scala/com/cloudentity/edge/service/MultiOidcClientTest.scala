package com.cloudentity.edge.service

import com.nimbusds.jose.jwk.JWKSet
import com.cloudentity.edge.util.{JwtUtils, SecurityUtils}
import com.cloudentity.tools.vertx.bus.ServiceClientFactory
import com.cloudentity.tools.vertx.test.{VertxDeployTest, VertxUnitTest}
import io.vertx.core.Future
import io.vertx.core.json.{JsonArray, JsonObject}
import io.vertx.ext.unit.TestContext
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}
import scalaz.{-\/, \/, \/-}

class MultiOidcClientTest extends VertxUnitTest with JwtUtils with SecurityUtils {

  var idp1: ClientAndServer = _
  var idp2: ClientAndServer = _

  var idp1Port = 9907
  var idp2Port = 9908

  @Before
  def before(): Unit = {
    idp1 = startClientAndServer(idp1Port)
    idp2 = startClientAndServer(idp2Port)
  }

  @After
  def after(): Unit = {
    idp1.stop()
    idp2.stop()
  }

  @Test
  def getPublicKeysFromMultipleIdps(ctx: TestContext): Unit = {
    mockJwk(idp1, jwkKeys("kid1"))
    mockJwk(idp2, jwkKeys("kid2"))

    val client = ServiceClientFactory.make(vertx().eventBus(), classOf[OidcClient])

    VertxDeployTest.deployWithConfig(vertx(), new MultiOidcClient(), verticleConfig)
      .compose { _ => client.getPublicKeys() }
      .compose { keySet => assertKeySetEquals(keySet, ctx, 2) }
      .compose { _ => {
        ctx.async().complete()
        Future.succeededFuture[Void]()
      }}
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def getPublicKeysFromMultipleIdpsWhenOneIdpIsNotAvailable(ctx: TestContext): Unit = {
    mockJwkError(idp1, 404)
    mockJwk(idp2, jwkKeys("kid2"))

    val client = ServiceClientFactory.make(vertx().eventBus(), classOf[OidcClient])

    VertxDeployTest.deployWithConfig(vertx(), new MultiOidcClient(), verticleConfig)
      .compose { _ => client.getPublicKeys() }
      .compose { keySet => assertKeySetEquals(keySet, ctx, 1) }
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

  private def mockJwk(mock: ClientAndServer, body: String) = {
    mock.when(HttpRequest.request().withMethod("GET").withPath("/oauth/jwk"))
      .respond(HttpResponse.response(body).withStatusCode(200))
  }

  private def mockJwkError(mock: ClientAndServer, statusCode: Int) = {
    mock.when(HttpRequest.request().withMethod("GET").withPath("/oauth/jwk"))
      .respond(HttpResponse.response().withStatusCode(statusCode))
  }

  private def jwkKeys(kid: String): String = {
    val keyPair = generateRsaKeyPair
    val jwk = toJwkSet(generateRsaJwk(keyPair, kid))
    jwk.toString
  }

  private def verticleConfig(): JsonObject = {
    val idps = new JsonArray()
      .add(idpConfig("localhost", "/oauth", "/jwk", idp1Port))
      .add(idpConfig("localhost", "/oauth", "/jwk", idp2Port))

    new JsonObject()
      .put("jwkReload", 1000)
      .put("idps", idps)
  }

  def idpConfig(host: String, basePath: String, jwkEndpoint: String, port: Int): JsonObject = {
    new JsonObject()
      .put("host", host)
      .put("basePath", basePath)
      .put("jwkEndpoint", jwkEndpoint)
      .put("port", port)
  }

}
