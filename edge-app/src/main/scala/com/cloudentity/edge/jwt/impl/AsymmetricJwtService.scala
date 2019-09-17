package com.cloudentity.edge.jwt.impl

import java.security.PrivateKey
import java.time.{Duration, ZoneOffset, ZonedDateTime}
import java.util.UUID

import io.circe.{Decoder, Json, parser}
import com.cloudentity.edge.jwt._
import com.cloudentity.edge.util.ConfigDecoder
import com.cloudentity.tools.vertx.scala.Futures
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.vertx.core.json.JsonObject
import io.vertx.core.{Future => VxFuture}
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import pdi.jwt.algorithms.JwtAsymetricAlgorithm
import pdi.jwt.{Jwt, JwtAlgorithm, JwtOptions}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scalaz._
import Scalaz._

case class AsymmetricJwtConf(
  serialNumber: String,       // kid
  serviceId: Option[String],  // iss, deprecated, use spiffeId instead
  instanceId: Option[String], // iid, deprecated, use spiffeId instead
  spiffeId: Option[String],   // spiffeId, spiffe://cluster.local/ns/default/sa/default/ver/1/ins/1
  expiresIn: Duration,        // exp
  algorithm: Option[String],  // alg
  privateKey: Option[String],
  keystore: Option[Keystore],
  toleranceInSeconds: Option[Long]
)

object AsymmetricJwtConf {
  import io.circe.generic.semiauto._

  implicit val durationDecoder: Decoder[Duration] = Decoder.decodeString.emapTry(value => Try(Duration.parse(value)))
  implicit val keystoreJwtConfDecoder: Decoder[Keystore] = deriveDecoder[Keystore]
  implicit val jwtIssuerJwtConfDecoder: Decoder[JwtIssuer] = deriveDecoder[JwtIssuer]
  implicit val asymmetricJwtConfDecoder: Decoder[AsymmetricJwtConf] = deriveDecoder[AsymmetricJwtConf]
}

class AsymmetricJwtService extends ScalaServiceVerticle with JwtService with ConfigDecoder {
  var algorithm: JwtAsymetricAlgorithm = _
  var asymmetricConfig: AsymmetricJwtConf = _
  var privateKey: PrivateKey = _
  var keyValidator: PublicKeyValidator = _
  var keyProvider: PublicKeyProvider = _
  var serviceIdFields: List[(String, Json)] = List()
  var jwtOptions: JwtOptions = _

  val defaultToleranceInSeconds = 5

  override def initService(): Unit = {
    val cfg = decodeConfigUnsafe[AsymmetricJwtConf]
    initialize(cfg, createClient(classOf[PublicKeyValidator]), createClient(classOf[PublicKeyProvider]))
  }

  override def vertxServiceAddressPrefixS: Option[String] = Some {
    super.vertxServiceAddressPrefixS.getOrElse("asymmetric")
  }

  def initialize(cfg: AsymmetricJwtConf, validator: PublicKeyValidator, provider: PublicKeyProvider): Unit = {
    asymmetricConfig = cfg
    keyValidator = validator
    keyProvider = provider

    algorithm = cfg.algorithm
      .map(JwtAlgorithm.fromString)
      .getOrElse(JwtAlgorithm.RS256)
      .asInstanceOf[JwtAsymetricAlgorithm]

    privateKey = asymmetricConfig.privateKey.map(PKCS1Keys.readBase64PrivateKey)
      .orElse(asymmetricConfig.keystore.map(_.readPrivateKey()))
      .get.get

    evaluateServiceIdFields match {
      case Left(ex)  => throw ex
      case Right(fs) => serviceIdFields = fs
    }

    jwtOptions =
      JwtOptions(
        signature  = true,
        expiration = true,
        notBefore  = true,
        leeway     = asymmetricConfig.toleranceInSeconds.getOrElse(defaultToleranceInSeconds)
      )
  }

  def evaluateServiceIdFields: Either[Exception, List[(String, Json)]] = {
    asymmetricConfig.spiffeId match {
      case None =>
        val fields = for {
          iss <- asymmetricConfig.serviceId
          iid <- asymmetricConfig.instanceId
        } yield List(("iss", Json.fromString(iss)), ("iid", Json.fromString(iid)))

        fields match {
          case Some(fs) => Right(fs)
          case None     => Left(new Exception("serviceId or instanceId is missing"))
        }
      case Some(sid) =>
        SpiffeId.fromString(sid) match {
          case Left(err) =>
            Left(new Exception(err.toString))
          case Right(spiffeId) =>
            Right(spiffeId.toJsonFields)
        }
    }
  }

  def setExecutionContext(ec: VertxExecutionContext): Unit = this.executionContext = ec
  override def empty(): VxFuture[JwtToken] = VxFuture.succeededFuture {
    val issuedAt = ZonedDateTime.now(ZoneOffset.UTC)
    val expiration = issuedAt.plus(asymmetricConfig.expiresIn)
    val issuedAtWithTolerance = issuedAt.minusSeconds(asymmetricConfig.toleranceInSeconds.getOrElse(defaultToleranceInSeconds))
    JwtToken(
      Json.obj(
        ("jti", Json.fromString(UUID.randomUUID().toString)),
        ("iat", Json.fromLong(issuedAtWithTolerance.toEpochSecond)),
        ("exp", Json.fromLong(expiration.toEpochSecond)),
        ("nbf", Json.fromLong(issuedAtWithTolerance.toEpochSecond))
      ).deepMerge(Json.obj(serviceIdFields:_*))
    )
  }

  override def sign(token: JwtToken): VxFuture[String] = tryFuture {
    val header = new JsonObject()
      .put("alg", algorithm.name)
      .put("kid", asymmetricConfig.serialNumber)
      .toString

    Jwt.encode(header, token.claims.noSpaces, privateKey, algorithm)
  }

  override def parse(encodedToken: String): VxFuture[JwtToken] = {
    val result = for {
      issuer      <- Future.fromTry[JwtIssuer](JwtIssuer.decode(encodedToken))
      certificate <- keyProvider.getPublicKey(issuer.serialNumber).toScala() |>
                       getOrElse(new Exception(s"Could not find public key for ${issuer.serialNumber}"))
      // _        <- keyValidator.verifyCertificateOwner("", certificate).toScala() // TODO certificate verification
      decoded     <- Future.fromTry(Jwt.decode(encodedToken, certificate.getPublicKey, jwtOptions))
      claims      <- Future.fromTry(parser.parse(decoded).toTry)
    } yield JwtToken(claims)

    Futures.toJava(result)
  }

  override def emptySigned: VxFuture[(JwtToken, String)] =
    empty().compose(token => sign(token).map(signed => (token, signed)))

  private def tryFuture[A](f: => A): VxFuture[A] = tryFutureT(Try(f))

  private def tryFutureT[A](t: => Try[A]): VxFuture[A] = t match {
    case Success(a)   => VxFuture.succeededFuture(a)
    case Failure(err) => VxFuture.failedFuture(err)
  }

  private def getOrElse[A](err: Throwable)(f: Future[Option[A]]): Future[A] = f.flatMap {
    case Some(a) => Future.successful(a)
    case None    => Future.failed(err)
  }
}
