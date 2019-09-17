package com.cloudentity.edge.jwt.impl

import java.time.{Duration, ZoneOffset, ZonedDateTime}
import java.util.UUID

import com.nimbusds.jwt.JWTClaimsSet
import io.circe._
import io.circe.parser
import io.jsonwebtoken.{JwtParser, Jwts}
import com.cloudentity.edge.jwt.{JwtService, JwtToken}
import com.cloudentity.edge.util.ConfigDecoder
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.vertx.core.{Future => VxFuture}
import pdi.jwt.algorithms.JwtHmacAlgorithm
import pdi.jwt.{Jwt, JwtAlgorithm}

import scala.util.{Failure, Success, Try}

case class SymmetricJwtConf(secret: String, issuer: String, expiresIn: Duration, algorithm: Option[String], toleranceInSeconds: Option[Long])

object SymmetricJwtConf {
  import io.circe.generic.semiauto._

  implicit val durationDecoder: Decoder[Duration] = Decoder.decodeString.emapTry(value => Try(Duration.parse(value)))
  implicit val symmetricJwtConfDecoder: Decoder[SymmetricJwtConf] = deriveDecoder[SymmetricJwtConf]
}

object SymmetricJwtService {
  def build(conf: SymmetricJwtConf): SymmetricJwtService = {
    val service = new SymmetricJwtService
    service.initialize(conf)
    service
  }
}

class SymmetricJwtService extends ScalaServiceVerticle with JwtService with ConfigDecoder {
  var algorithm: JwtAlgorithm = _
  var symmetricConfig: SymmetricJwtConf = _
  var jwtParser: JwtParser = _

  val defaultToleranceInSeconds = 5

  override def initService(): Unit =
    initialize(decodeConfigUnsafe[SymmetricJwtConf])

  def initialize(cfg: SymmetricJwtConf): Unit = {
    symmetricConfig = cfg
    algorithm = cfg.algorithm
      .map(JwtAlgorithm.fromString)
      .getOrElse(JwtAlgorithm.HS256)
      .asInstanceOf[JwtHmacAlgorithm]

    jwtParser = Jwts.parser()
      .setAllowedClockSkewSeconds(symmetricConfig.toleranceInSeconds.getOrElse(defaultToleranceInSeconds))
      .setSigningKey(symmetricConfig.secret.getBytes())
  }

  override def vertxServiceAddressPrefixS: Option[String] = Some {
    super.vertxServiceAddressPrefixS.getOrElse("symmetric")
  }

  override def sign(token: JwtToken): VxFuture[String] = tryFuture {
    Jwt.encode(token.claims.noSpaces, symmetricConfig.secret, algorithm)
  }

  override def parse(encodedToken: String): VxFuture[JwtToken] = tryFutureT {
    for {
      rawClaims <- Try {
        val claims = jwtParser.parseClaimsJws(encodedToken).getBody
        val builder = new JWTClaimsSet.Builder()
        claims.forEach((k, v) => builder.claim(k, v))
        builder.build()
      }
      claims <- parser.parse(rawClaims.toString).toTry
    } yield JwtToken(claims)
  }

  override def empty() = VxFuture.succeededFuture {
    val issuedAt = ZonedDateTime.now(ZoneOffset.UTC)
    val expiration = issuedAt.plus(symmetricConfig.expiresIn)
    val issuedAtWithTolerance = issuedAt.minusSeconds(symmetricConfig.toleranceInSeconds.getOrElse(defaultToleranceInSeconds))
    JwtToken(
      Json.obj(
        ("jti", Json.fromString(UUID.randomUUID().toString)),
        ("iss", Json.fromString(symmetricConfig.issuer)),
        ("iat", Json.fromLong(issuedAtWithTolerance.toEpochSecond)),
        ("exp", Json.fromLong(expiration.toEpochSecond)),
        ("nbf", Json.fromLong(issuedAtWithTolerance.toEpochSecond))
      )
    )
  }

  override def emptySigned: VxFuture[(JwtToken, String)] =
    empty().compose(token => sign(token).map(signed => (token, signed)))

  private def tryFuture[A](f: => A): VxFuture[A] = tryFutureT(Try(f))

  private def tryFutureT[A](t: => Try[A]): VxFuture[A] = t match {
    case Success(a)   => VxFuture.succeededFuture(a)
    case Failure(err) => VxFuture.failedFuture(err)
  }
}
