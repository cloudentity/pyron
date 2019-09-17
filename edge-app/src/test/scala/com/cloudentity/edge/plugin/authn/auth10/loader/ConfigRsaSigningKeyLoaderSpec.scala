package com.cloudentity.edge.plugin.authn.auth10.loader

import java.util
import java.util.Base64

import com.cloudentity.edge.plugin.impl.authn.methods.oauth10.loader.{ConfigRsaSigningKeyLoader, KeyLoaderKeyNotFound, SigningKeyLoader, SigningKeyLoaderError}
import com.cloudentity.edge.util.SecurityUtils
import com.cloudentity.tools.vertx.bus.ServiceClientFactory
import com.cloudentity.tools.vertx.test.{VertxDeployTest, VertxUnitTest}
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import org.junit.{Before, Test}
import scalaz.{-\/, \/, \/-}

class ConfigRsaSigningKeyLoaderSpec extends VertxUnitTest with SecurityUtils {

  var keyLoader: ConfigRsaSigningKeyLoader = _

  @Before
  def before(): Unit = {
    keyLoader = new ConfigRsaSigningKeyLoader()
  }

  @Test
  def loadKey(ctx: TestContext): Unit = {
    val (pub, _) = generateRsaKeyPairTuple
    val lookupKey = "key1"
    val rawPubKey = pub.getEncoded
    val encodedKeyValue = Base64.getEncoder.encodeToString(rawPubKey)


    val config = new JsonObject().put("publicKeys", new JsonObject().put("key1", encodedKeyValue))

    deployAndLoadKey(config, lookupKey)
      .compose { res => res match {
          case \/-(key) => ctx.assertTrue(util.Arrays.equals(rawPubKey, key))
          case -\/(_) => ctx.fail()
        }
        Future.succeededFuture(())
      }.setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def loadNonexistentKey(ctx: TestContext): Unit = {
    val config = new JsonObject().put("publicKeys", new JsonObject())

    deployAndLoadKey(config, "nonexistent")
      .compose { res => res match {
          case \/-(_) => ctx.fail()
          case -\/(ex) => ex match {
            case KeyLoaderKeyNotFound() => ctx.assertTrue(true)
            case _ => ctx.fail()
          }
        }
        Future.succeededFuture(())
      }.setHandler(ctx.asyncAssertSuccess())
  }

  def deployAndLoadKey(config: JsonObject, key: String): Future[SigningKeyLoaderError \/ Array[Byte]] = {
    val client = ServiceClientFactory.make(vertx().eventBus(), classOf[SigningKeyLoader])
    VertxDeployTest.deployWithConfig(vertx(), keyLoader, keyLoader.verticleId(), config)
      .compose { _ => client.load(key) }
  }

}
