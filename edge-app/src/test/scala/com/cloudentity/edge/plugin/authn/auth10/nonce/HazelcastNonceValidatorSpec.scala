package com.cloudentity.edge.plugin.authn.auth10.nonce

import com.cloudentity.edge.plugin.impl.authn.methods.oauth10.OAuth10Request
import com.cloudentity.edge.plugin.impl.authn.methods.oauth10.nonce.{HazelcastNonceValidator, NonceNotUnique, NonceValidator}
import com.cloudentity.tools.vertx.hazelcast.inmemory.InMemoryHazelcastVerticle
import com.cloudentity.tools.vertx.test.VertxUnitTest
import com.cloudentity.tools.vertx.verticles.VertxDeploy
import io.vertx.ext.unit.TestContext
import io.vertx.core.{Future => VxFuture}
import org.junit.{Before, Test}
import scalaz.{-\/, \/-}

class HazelcastNonceValidatorSpec extends VertxUnitTest {

  var nonceValidator: HazelcastNonceValidator = _
  val params = OAuth10Request("123", None, "signature", "method", 123, "nonce", None)

  @Before
  def before(): Unit = {
    nonceValidator = new HazelcastNonceValidator()
  }

  def deploy(): VxFuture[String] = {
    VertxDeploy.deploy(vertx(), new InMemoryHazelcastVerticle())
      .compose(_ => VertxDeploy.deploy(vertx(), nonceValidator))
  }

  @Test
  def validateNonce(ctx: TestContext): Unit = {
      deploy()
      .compose(_ => nonceValidator.validate(params))
      .compose { res =>
        res match {
          case \/-(_) => ctx.assertTrue(true)
          case -\/(_) => ctx.fail()
        }
        VxFuture.succeededFuture(())
      }
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def validateTheSameRequestTwice(ctx: TestContext): Unit = {
    deploy()
      .compose(_ => nonceValidator.validate(params))
      .compose(_ => nonceValidator.validate(params))
      .compose { res =>
        res match {
          case \/-(_) => ctx.fail("Nonce should fail")
          case -\/(ex) => ctx.assertEquals(NonceNotUnique, ex)
        }
        VxFuture.succeededFuture(())
      }
      .setHandler(ctx.asyncAssertSuccess())
  }


}
