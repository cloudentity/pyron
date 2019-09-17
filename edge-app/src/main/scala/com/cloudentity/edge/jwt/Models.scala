package com.cloudentity.edge.jwt

import java.io.{FileInputStream, StringReader}
import java.security.cert.X509Certificate
import java.security.{KeyStore, PrivateKey}
import java.util.Base64

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.{JWSVerificationKeySelector, SecurityContext}
import com.nimbusds.jose.{JWSAlgorithm, JWSObject}
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import io.circe.{Encoder, Json}
import com.cloudentity.edge.domain.flow.AuthnCtx
import io.vertx.core.json.JsonObject
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser}
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter

import scala.util.Try

case class JwtClaim(name: String, value: Json)

case class JwtToken(claims: Json) {
  def put[A](key: String, value: A)(implicit e: Encoder[A]): JwtToken =
    JwtToken(claims.deepMerge(content(Json.obj((key, e.apply(value))))))

  def withClaims(newClaims: Json): JwtToken =
    JwtToken(claims.deepMerge(newClaims))

  def withClaims(cs: List[JwtClaim]): JwtToken =
    cs.foldLeft(this)((jwt, claim) => jwt.put(claim.name, claim.value))

  def withCtxClaims(cs: AuthnCtx): JwtToken = cs.value.foldLeft(this) { case (jwt, (name, value)) => jwt.put(name, value) }

  private def content(json: Json) = Json.obj(("content", json))
}

case class Keystore(
  path: String,
  password: Option[String],
  keyAlias: String,
  keyPassword: String
) {
  def readPrivateKey(): Try[PrivateKey] =
    readKeystore().flatMap(ks => Try(ks.getKey(keyAlias, keyPassword.toCharArray).asInstanceOf[PrivateKey]))

  def readCertificate(): Try[X509Certificate] =
    readKeystore().flatMap(ks => Try(ks.getCertificate(keyAlias).asInstanceOf[X509Certificate]))

  def readKeystore(): Try[KeyStore] = Try {
    val is = new FileInputStream(path)
    val keystore = KeyStore.getInstance(KeyStore.getDefaultType)
    keystore.load(is, password.map(_.toCharArray).orNull)
    keystore
  }
}

object PKCS1Keys {
  def readBase64PrivateKey(key: String): Try[PrivateKey] = Try {
    val decoded = new String(Base64.getDecoder.decode(key))
    val pemParser = new PEMParser(new StringReader(decoded.replace("\\n", "\n")))
    val converter = new JcaPEMKeyConverter()
    val obj = pemParser.readObject()
    val kp = converter.getKeyPair(obj.asInstanceOf[PEMKeyPair])
    kp.getPrivate
  }
}

case class JwtIssuer(
  serialNumber: String, // kid
  serviceId: String,    // iss
  instanceId: String    // iid
)

object JwtIssuer {
  def decode(encodedToken: String): Try[JwtIssuer] = Try {
    encodedToken.split("\\.") match {
      case Array(header, payload, _) =>
        val decoder = java.util.Base64.getDecoder
        val headerJson = new JsonObject(new String(decoder.decode(header)))
        val payloadJson = new JsonObject(new String(decoder.decode(payload)))

        JwtIssuer(
          Option(headerJson.getString("kid")).get,
          Option(payloadJson.getString("iss")).get,
          Option(payloadJson.getString("iid")).get
        )
      case _ => throw new Exception("Jwt must contain 3 parts")
    }
  }
}

object OAuthAccessToken {
  def parse(token: String, keySet: JWKSet): Either[Throwable, JWTClaimsSet] = {
    def alg: JWSAlgorithm = Try(JWSObject.parse(token).getHeader.getAlgorithm).getOrElse {
      JWSAlgorithm.RS256
    }
    val jwtProcessor = new DefaultJWTProcessor[SecurityContext]()
    val keySelector = new JWSVerificationKeySelector(alg, new ImmutableJWKSet[SecurityContext](keySet))
    jwtProcessor.setJWSKeySelector(keySelector)
    Try(jwtProcessor.process(token, null)).toEither
  }
}
