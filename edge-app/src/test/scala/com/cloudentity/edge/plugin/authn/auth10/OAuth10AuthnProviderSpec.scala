package com.cloudentity.edge.plugin.authn.auth10

import com.cloudentity.edge.plugin.impl.authn.AuthnMethodConf
import com.cloudentity.edge.plugin.impl.authn.AuthnPlugin.AuthnProviderResult
import com.cloudentity.edge.plugin.impl.authn.methods.oauth10._
import com.cloudentity.edge.test.TestRequestResponseCtx
import com.cloudentity.edge.util.FutureUtils
import com.cloudentity.tools.vertx.test.VertxUnitTest
import io.vertx.core.{Future => VxFuture}
import io.vertx.ext.unit.TestContext
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import org.junit.{Before, Test}
import org.scalatest.MustMatchers

class OAuth10AuthnProviderSpec extends VertxUnitTest with MustMatchers with TestRequestResponseCtx with FutureUtils {
  implicit var ec: VertxExecutionContext = _

  var provider: OAuth10AuthnProvider = _
  val publicDomainConf = PublicDomainConf("example.com", 8080, false)
  val headerConf = HeaderConf("Authorization", "OAuth (.*)".r)
  val providerConf = OAuth10AuthnProviderConf(headerConf, publicDomainConf, None, None, None, None, None)

  @Before
  def setup() = {
    ec = VertxExecutionContext(vertx.getOrCreateContext())
    provider = new OAuth10AuthnProvider()
  }

  @Test
  def shouldSkipAuthenticationIfAuthorizationHeaderIsMissing(ctx: TestContext): Unit = {
    val req = emptyRequestCtx
    provider.conf = providerConf

    provider.authenticate(req, AuthnMethodConf(None))
      .compose { result: Option[AuthnProviderResult] =>
        ctx.assertTrue(result.isEmpty)
        VxFuture.succeededFuture[Void]()
      }.setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def shouldSkipAuthenticationIfAuthorizationHeaderHasInvalidFormat(ctx: TestContext): Unit = {
    val req = emptyRequestCtx.modifyRequest(_.withHeader("Authorization", "123"))
    provider.conf = providerConf

    provider.authenticate(req, AuthnMethodConf(None))
      .compose { result: Option[AuthnProviderResult] =>
        ctx.assertTrue(result.isEmpty)
        VxFuture.succeededFuture[Void]()
      }.setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def shouldValidateTimestampWithClientClock2SecondsBeforeServer(ctx: TestContext): Unit = {
    val skewTimeInSeconds = 5
    val conf =  OAuth10AuthnProviderConf(headerConf, publicDomainConf, Some(skewTimeInSeconds), None, None, None, None)
    provider.conf = conf

    val timestamp = System.currentTimeMillis() / 1000 - 2
    val params = requestWithTimestamp(timestamp)

    val result = provider.validateTimestamp(params)
    ctx.assertTrue(result.isRight)
  }

  @Test
  def shouldFailToValidateTimestamp(ctx: TestContext): Unit = {
    val skewTimeInSeconds = 0
    provider.conf = OAuth10AuthnProviderConf(headerConf, publicDomainConf, Some(skewTimeInSeconds), None, None, None, None)

    val timestamp = System.currentTimeMillis() / 1000 - 1
    val request = requestWithTimestamp(timestamp)

    val result = provider.validateTimestamp(request)
    ctx.assertTrue(result.isLeft)
  }

  def requestWithTimestamp(timestamp: Long): OAuth10Request =
    OAuth10Request("key", None, "signature", "method", timestamp, "nonce", None)

}
