package com.cloudentity.edge.plugin.impl.authn

import java.{lang, util}

import io.circe.{Json, JsonObject}
import com.cloudentity.edge.domain._
import com.cloudentity.edge.plugin.impl.authn.AuthnMethodConf
import com.cloudentity.edge.plugin.impl.authn.AuthnPlugin.{AuthnFailure, AuthnProviderResult, AuthnSuccess}
import com.cloudentity.edge.plugin.impl.authn.methods.{OAuthAuthorizationCodeIntrospection, OAuthTokenIntrospectClient}
import com.cloudentity.edge.test.TestRequestResponseCtx
import io.orchis.tools.jwt.JwtClaims
import com.cloudentity.tools.vertx.jwt.api.JwtService
import com.cloudentity.tools.vertx.http.Headers
import com.cloudentity.tools.vertx.test.VertxUnitTest
import io.vavr.control
import io.vertx.core.{Future => VxFuture}
import io.vertx.ext.unit.TestContext
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import org.junit.{Before, Test}
import org.scalatest.MustMatchers

import scala.concurrent.Future
import scalaz.{\/, \/-}

class OAuthAuthorizationCodeIntrospectionSpec extends VertxUnitTest with MustMatchers with TestRequestResponseCtx {
  implicit var ec: VertxExecutionContext = _

  @Before
  def setup() = {
    ec = VertxExecutionContext(vertx.getOrCreateContext())
  }


  @Test
  def shouldAbstainIfTokenMissing(ctx: TestContext): Unit = {
    val requestCtx = emptyRequestCtx
    val oidcResult = Future.successful(\/-(JsonObject.empty))

    providerWithOidcMock(oidcResult).authenticate(requestCtx, AuthnMethodConf(None))
        .compose { result: Option[AuthnProviderResult] =>
          ctx.assertTrue(result.isEmpty)

          VxFuture.succeededFuture[Void]()
        }
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def shouldReturnFailureIfTokenInactive(ctx: TestContext): Unit = {
    val requestCtx = emptyRequestCtx.modifyRequest(_.copy(headers = Headers("Authorization" -> List("Bearer x"))))
    val oidcResult = Future.successful(\/-(JsonObject.singleton("active", Json.fromBoolean(false))))

    providerWithOidcMock(oidcResult).authenticate(requestCtx, AuthnMethodConf(None))
      .compose { result: Option[AuthnProviderResult] =>
        result match {
          case Some(AuthnFailure(_, _)) => // success
          case _ => ctx.fail("expected Authn failure")
        }

        VxFuture.succeededFuture[Void]()
      }
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def shouldReturnSuccessIfTokenActiveAndDropActiveAttributeAndSplitScopesAndSetTokenInAuthnCtx(ctx: TestContext): Unit = {
    val token = "x"
    val requestCtx = emptyRequestCtx.modifyRequest(_.copy(headers = Headers("Authorization" -> List(s"Bearer $token"))))
    val tokenContent =
      JsonObject
        .singleton("active", Json.fromBoolean(true))
        .add("field", Json.fromString("value"))
        .add("scope", Json.fromString("a b"))

    val oidcResult = Future.successful(\/-(tokenContent))

    providerWithOidcMock(oidcResult).authenticate(requestCtx, AuthnMethodConf(None))
      .compose { result: Option[AuthnProviderResult] =>
        result match {
          case Some(AuthnSuccess(authnCtx, _)) =>
            ctx.assertEquals(authnCtx.get("field"), Some(Json.fromString("value")))
            ctx.assertEquals(authnCtx.get("active"), None, "should drop 'active' attribute")
            ctx.assertEquals(authnCtx.get("scope"), Some(Json.arr(Json.fromString("a"), Json.fromString("b"))), "should split 'scope' attribute")
            ctx.assertEquals(authnCtx.get("token"), Some(Json.fromString(token)), "should set 'token' attribute")
          case _ => ctx.fail("expected Authn success")
        }

        VxFuture.succeededFuture[Void]()
      }
      .setHandler(ctx.asyncAssertSuccess())
  }

  def providerWithOidcMock(f: Future[Throwable \/ JsonObject]): OAuthAuthorizationCodeIntrospection =
    new OAuthAuthorizationCodeIntrospection(mockJwtService(), mockOidc(f))

  def mockJwtService(): JwtService = new JwtService {
    override def sign(): VxFuture[control.Either[Throwable, String]] = VxFuture.succeededFuture(control.Either.right(""))

    override def canDecode(s: String): VxFuture[lang.Boolean] = ???
    override def sign(map: util.Map[String, AnyRef]): VxFuture[control.Either[Throwable, String]] = ???
    override def decode(s: String): VxFuture[control.Either[Throwable, JwtClaims]] = ???
  }

  def mockOidc(f: Future[Throwable \/ JsonObject]): OAuthTokenIntrospectClient =
    (_, _, _) => f
}
