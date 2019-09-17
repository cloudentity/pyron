package com.cloudentity.edge.plugin.authn

import java.util.Optional

import io.circe.Json
import io.circe.generic.semiauto._
import com.cloudentity.edge.api.Responses
import com.cloudentity.edge.api.Responses.Errors
import com.cloudentity.edge.plugin.impl.authn.{AuthnMethodConf, AuthnProvider}
import com.cloudentity.edge.plugin.impl.authn.AuthnPlugin.{AuthnFailure, AuthnProviderResult, AuthnSuccess, Modify}
import com.cloudentity.edge.plugin.impl.authn.methods.{CloudSsoAuthnProviderConf, SsoAuthnProvider}
import com.cloudentity.edge.plugin.impl.session.SessionServiceConf
import com.cloudentity.tools.vertx.scala.FutureConversions
import com.cloudentity.tools.vertx.test.{VertxDeployTest, VertxUnitTest}
import io.vertx.ext.unit.TestContext
import org.junit.Test
import org.scalatest.MustMatchers
import io.vertx.core.{Future => VxFuture}

import scala.concurrent.Future
import io.circe.syntax._
import com.cloudentity.edge.cookie.CookieSettings
import com.cloudentity.edge.domain.flow.RequestCtx
import com.cloudentity.edge.domain.http.ApiResponse
import com.cloudentity.edge.service.session.SessionServiceClient
import com.cloudentity.edge.test.TestRequestResponseCtx
import com.cloudentity.tools.vertx.bus.ServiceClientFactory
import com.cloudentity.tools.vertx.http.Headers
import io.vertx.core.buffer.Buffer

class SSOPluginTest extends VertxUnitTest with MustMatchers with FutureConversions  with TestRequestResponseCtx {
  implicit lazy val CookieSettingsEnc = deriveEncoder[CookieSettings]
  implicit lazy val SessionServiceConfEnc = deriveEncoder[SessionServiceConf]
  implicit lazy val SsoAuthnProviderConfEnc = deriveEncoder[CloudSsoAuthnProviderConf]

  val provider = new SsoAuthnProvider {
    override def callGetSession(ctx: RequestCtx, token: String): Future[Option[SessionServiceClient.Session]] =
      Future.successful(Json.obj().asObject)
  }

  val config = CloudSsoAuthnProviderConf(SessionServiceConf(false, "", 0, "", 0, false), Some("token"), Some("token"), None, Option("X-CSRF-TOKEN"), "", "")

  @Test
  def whenNoTokenAtAllThenPassIt(ctx: TestContext): Unit = {
    deployProvider()
      .compose(_.authenticate(emptyRequestCtx, AuthnMethodConf(None)))
      .compose { assertion(_ mustBe(None))(_) }
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def whenSsoTokenWithoutPrefixThenGetSession(ctx: TestContext): Unit = {
    val requestCtx = emptyRequestCtx.modifyRequest(_.modifyHeaders(_.set("token", "xyz")))

    deployProvider()
      .compose(_.authenticate(requestCtx, AuthnMethodConf(None)))
      .compose { assertion(_.get.isInstanceOf[AuthnSuccess] mustBe(true))(_) }
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def whenSsoTokenWithPrefixThenGetSession(ctx: TestContext): Unit = {
    val requestCtx = emptyRequestCtx.modifyRequest(_.modifyHeaders(_.set("Authorization", "sso xyz")))

    deployProvider()
      .compose(_.authenticate(requestCtx, AuthnMethodConf(Some("Authorization"))))
      .compose { assertion(_.get.isInstanceOf[AuthnSuccess] mustBe(true))(_) }
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def whenNotSsoTokenThenPassIt(ctx: TestContext): Unit = {
    val requestCtx = emptyRequestCtx.modifyRequest(_.modifyHeaders(_.set("Authorization", "Bearer xyz")))

    deployProvider()
      .compose(_.authenticate(requestCtx, AuthnMethodConf(None)))
      .compose { assertion(_ mustBe(None))(_) }
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def whenContainsCorrectCsrfHeaderButCouldNotFindTokenInCookieThenPassIt(ctx: TestContext): Unit = {
    val requestCtx = emptyRequestCtx.modifyRequest(_.copy(headers = Headers("X-CSRF-TOKEN" -> List("."))))

    deployProvider()
      .compose(_.authenticate(requestCtx, AuthnMethodConf(None)))
      .compose { assertion(_ mustBe(None))(_) }
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def whenCookieContainsTokenButCsrfHeaderIsInvalidThenFail(ctx: TestContext): Unit = {
    val requestCtx = emptyRequestCtx.modifyRequest(_.copy(headers = Headers("INVALID-CSRF" -> List("."), "Cookie" -> List("token=123123-123123-123123-12313;"))))

    deployProvider()
      .compose(_.authenticate(requestCtx, AuthnMethodConf(None)))
      .compose { assertion(_.get mustBe(AuthnFailure(ApiResponse(400, Buffer.buffer(Responses.mkString(Errors.invalidCSRFHeader.body)), Headers.of("Content-Type" -> "application/json")), Modify.noop)))(_) }
      .setHandler(ctx.asyncAssertSuccess())
  }

  def assertion(f: Option[AuthnProviderResult] => Unit): Option[AuthnProviderResult] => VxFuture[Unit] =
    result => VxFuture.succeededFuture(f(result))

  def deployProvider(): VxFuture[AuthnProvider] =
    VertxDeployTest.deployWithConfig(vertx, provider, "sso", new io.vertx.core.json.JsonObject(config.asJson.toString))
      .compose(_ => VxFuture.succeededFuture(ServiceClientFactory.make(vertx.eventBus(), classOf[AuthnProvider], Optional.of("sso"))))
}
