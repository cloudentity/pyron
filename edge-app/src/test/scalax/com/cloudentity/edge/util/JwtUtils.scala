package com.cloudentity.edge.util

import java.security.{KeyPair, KeyPairGenerator}
import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}

import com.nimbusds.jose.{JWSAlgorithm, JWSHeader, JWSObject, Payload}
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.{JWK, JWKSet, RSAKey}
import com.cloudentity.edge.jwt.JwtService
import com.cloudentity.tools.vertx.scala.Futures
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import io.vertx.core.json.JsonObject

import scala.concurrent.Await
import scala.concurrent.duration._

trait JwtUtils {

  def generateRsaSignedJwt(keyPair: KeyPair, kid: String, payload: JsonObject): String = {
    val signer = new RSASSASigner(keyPair.getPrivate)
    val jwsObject = new JWSObject(
      new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(),
      new Payload(payload.toString))
    jwsObject.sign(signer)
    jwsObject.serialize()
  }

  def generateRsaJwk(keyPair: KeyPair, kid: String): JWK = {
    new RSAKey.Builder(keyPair.getPublic.asInstanceOf[RSAPublicKey])
      .privateKey(keyPair.getPrivate.asInstanceOf[RSAPrivateKey])
      .keyID(kid).build
  }

  def toJwkSet(jwk: JWK): JWKSet = {
    new JWKSet(jwk)
  }

  def asJwtJson(jwtService: JwtService, headerRegex: String = "Bearer (.*)")(implicit ec: VertxExecutionContext): String => JsonObject = header => {
    val token = headerRegex.r.findFirstMatchIn(header).map(_.group(1)).getOrElse("")
    val future = Futures
      .toScala(jwtService.parse(token))
      .map(_.claims.noSpaces)
      .map(new JsonObject(_))
    Await.result(future, 10.seconds)
  }
}
