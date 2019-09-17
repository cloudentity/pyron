package com.cloudentity.edge.plugin.authn

import io.circe.Json
import com.cloudentity.edge.jwt.impl.SymmetricJwtService
import com.cloudentity.edge.jwt.{JwtService, JwtToken}
import com.cloudentity.edge.plugin.impl.authn.AuthnPlugin.{AuthnFailure, AuthnProviderResult, AuthnSuccess}
import com.cloudentity.edge.plugin.impl.authn.{AuthnMethodConf, AuthnProvider}
import com.cloudentity.edge.plugin.impl.authn.methods.JwtAuthnProvider
import com.cloudentity.edge.test.TestRequestResponseCtx
import com.cloudentity.tools.vertx.test.ServiceVerticleIntegrationTest
import io.vertx.core.json.JsonObject
import io.vertx.core.{Future, Verticle}
import io.vertx.ext.unit.TestContext
import org.junit.{Assert, Test}

import scala.collection.JavaConverters._

class JwtAuthnProviderSpec extends ServiceVerticleIntegrationTest with TestRequestResponseCtx {

  val jwtAuthnConfig = new JsonObject().put("jwtServiceAddress", "symmetric").put("mapping", new JsonObject().put("userUuid", "${sub}").put("token", "${cloudentity_token}"))
  val jwtServiceConfig = new JsonObject().put("secret", "secret").put("issuer", "").put("expiresIn", "PT60S")
  val verticleConfig = new JsonObject().put("jwtAuthn", jwtAuthnConfig).put("jwtService", jwtServiceConfig)

  val verticles = Map("jwtAuthn" -> new JwtAuthnProvider, "jwtService" -> new SymmetricJwtService)

  @Test
  def shouldDecodeSymmetricJwt(ctx: TestContext): Unit = {
    decodeSymmetricJwt(ctx, None)
  }

    @Test
  def shouldDecodeSymmetricJwtFromCustomHeader(ctx: TestContext): Unit = {
    decodeSymmetricJwt(ctx, Some("X-Original-Authorization"))
  }

  def decodeSymmetricJwt(ctx: TestContext, headerName: Option[String]): Unit = {
    deployVerticles(verticleConfig, verticles.asJava.asInstanceOf[java.util.Map[String, Verticle]])
      .compose { _ =>
        // given
        val jwt = JwtToken(Json.obj("sub" -> Json.fromString("xyz"), "cloudentity_token" -> Json.fromString("abc")))
        client(classOf[JwtService], "symmetric").sign(jwt)
      }
      .compose { (jwt: String) =>
        // when
        val req = emptyRequestCtx.modifyRequest(_.modifyHeaders(_.add(headerName.getOrElse("Authorization"), s"Bearer $jwt")))
        client(classOf[AuthnProvider], "jwtAuthn").authenticate(req, AuthnMethodConf(headerName))
      }.compose { (resultOpt: Option[AuthnProviderResult]) =>
        // then
        resultOpt match {
          case Some(AuthnSuccess(authnCtx, _)) =>
            val expected = Map("userUuid" -> Json.fromString("xyz"), "token" -> Json.fromString("abc"))
            Assert.assertEquals(expected, authnCtx.value)
          case Some(AuthnFailure(resp, _)) =>
            throw new Exception("authn failure: " + resp)
          case None =>
            throw new Exception("JwtAuthnProvider should find token")
        }
        Future.succeededFuture(())
      }.setHandler(ctx.asyncAssertSuccess())
  }
}
