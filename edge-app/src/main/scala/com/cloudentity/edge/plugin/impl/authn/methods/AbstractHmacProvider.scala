package com.cloudentity.edge.plugin.impl.authn.methods

import com.cloudentity.services.lla.client.api.UserApiClient
import com.cloudentity.services.openapi.tools.httpclient.vertxscala.ClientError
import com.cloudentity.services.openapi.tools.httpclient.vertxscala.auth.JwtAuth
import io.circe.Json
import io.circe.syntax._
import com.cloudentity.edge.api.Responses.{Error, Errors}
import com.cloudentity.edge.domain.flow.{AuthnCtx, RequestCtx}
import com.cloudentity.edge.domain.http.TargetRequest
import com.cloudentity.edge.domain.authn.CloudentityAuthnCtx
import com.cloudentity.edge.plugin.impl.authn.AuthnPlugin._
import com.cloudentity.edge.plugin.impl.authn.HmacKeyDecryptor._
import com.cloudentity.edge.plugin.impl.authn.{AuthnMethodConf, AuthnProvider, HmacHelper}
import com.cloudentity.tools.vertx.scala.Operation
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.vertx.core.{Future => VxFuture}
import com.cloudentity.edge.util.ConfigDecoder
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}

import scala.concurrent.Future
import scalaz.{-\/, \/-}

abstract class AbstractHmacProvider extends ScalaServiceVerticle with AuthnProvider with ConfigDecoder {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  var realm: String = _
  var hmac: HmacHelper = _
  var baseHmacProviderConf: BaseHmacProviderConf = _
  var userApi: UserApiClient = _
  var jwtAuthenticator: JwtAuth = _

  override def vertxServiceAddressPrefixS: Option[String] = Option(verticleId())

  override def authenticate(ctx: RequestCtx, methodConf: AuthnMethodConf): VxFuture[Option[AuthnProviderResult]] = {
    implicit val tracingCtx: TracingContext = ctx.tracingCtx
    readHmacData(ctx.request) match {
      case Right(data) => validate(ctx, data).map(Option.apply).toJava()
      case Left(err) => {
        log.error(tracingCtx, s"${err}")
        VxFuture.succeededFuture(Some(AuthnFailure(err.toApiResponse)))
      }
    }
  }

  def readHmacData(req: TargetRequest): Either[Error, HmacData] =
    for {
      authHeader <- req.headers.get(baseHmacProviderConf.authorization).toRight[Error](Errors.invalidRequest)
      date <- req.headers.get(baseHmacProviderConf.date).toRight[Error](Errors.invalidRequest)
      uuid <- hmac.getUUID(authHeader).toEither
      signature <- hmac.getSignature(authHeader).toEither
      userRealm = hmac.getRealmOrDefault(authHeader, realm)
    } yield HmacData(authHeader, date, uuid, signature, Some(userRealm))


  def decryptApiKey(apiKey: String, encryptionKey: String): HmacError \/ String = {
    apiKey.split(":").toList match {
      case salt :: encryptedKey :: Nil => decrypt(encryptedKey, encryptionKey, salt).leftMap(e => KeyDecryptionError(e.message))
      case _ => -\/(InvalidKeyFormat("Api key in user entry is not in format salt:encryptedKey"))
    }
  }

  def decryptApiKeyIfEncryptionRequired(apiKey: String): \/[HmacError, String] =
    baseHmacProviderConf.apiKeyEncryptionKey match {
      case Some(encryptionKey) => decryptApiKey(apiKey, encryptionKey)
      case None => \/-(apiKey)
    }

  def validate(ctx: RequestCtx, data: HmacData)(implicit tracingCtx: TracingContext): Future[AuthnProviderResult] = {
    val program: Operation[HmacError, AuthnProviderResult]  = for {
      _ <- hmac.validateDate(data.date).toOperation.leftMap[HmacError](err => ValidationError(err))
      user <- getUser(data).toOperation
      key <- getKey(user).toOperation
      plainTextApiKey <- Operation.fromEither(decryptApiKeyIfEncryptionRequired(key))
      acceptableHmacSignatures <- evaluateAcceptableHmacSignatures(ctx, plainTextApiKey, data).toOperation.leftMap[HmacError](err => ValidationError(err))
      _ <- hmac.validateSignatureMatchesAnyOfExpected(acceptableHmacSignatures, data.signature).toOperation.leftMap[HmacError](err => ValidationError(err))
    } yield AuthnSuccess(buildAuthnCtx(user, data))

    program.run.map {
      case \/-(res) => res
      case -\/(MissingKey()) => {
        log.error(ctx.tracingCtx ,s"No api key assigned to user")
        AuthnFailure(Errors.unauthenticated.toApiResponse, Modify.noop)
      }
      case -\/(InvalidKeyFormat(message)) => {
        log.error(ctx.tracingCtx , s"Error while reading user api key. Invalid key format. Details: $message")
        AuthnFailure(Errors.unauthenticated.toApiResponse, Modify.noop)
      }
      case -\/(KeyDecryptionError(message)) => {
        log.error(ctx.tracingCtx , s"Error while decrypting user api key. Details: $message")
        AuthnFailure(Errors.unauthenticated.toApiResponse, Modify.noop)
      }
      case -\/(LLAClientError(err)) => {
        log.error(ctx.tracingCtx , s"Error while getting ${data.realm}: ${err}")
        AuthnFailure(Errors.unauthenticated.toApiResponse, Modify.noop)
      }
      case -\/(ValidationError(err)) => {
        log.error(ctx.tracingCtx , s"Error while validating ${data.realm}: ${err}")
        AuthnFailure(err.toApiResponse, Modify.noop)
      }
    }
  }

  def evaluateAcceptableHmacSignatures(ctx: RequestCtx, apiKey: String, data: HmacData): \/[Error, List[String]]

  def getKey(user: Json): Future[\/[HmacError, String]] = {
    var program = for {
      jsonKey <- user.findAllByKey(baseHmacProviderConf.key).headOption.toOperation[HmacError](MissingKey())
      key <- jsonKey.asString.toOperation[HmacError](MissingKey())
    } yield key

    program.run
  }

  def getUser(data: HmacData)(implicit tracingCtx: TracingContext): Future[\/[HmacError, Json]] = {
    val jwtAuth = jwtAuthenticator.auth(Map())
    var program: Operation[HmacError, Json] = for {
      user <- userApi.getUser(tracingCtx, data.uuid, data.realm, jwtAuth).toOperation.leftMap[HmacError](e => LLAClientError(e))
    } yield user._props.asJson

    program.run
  }

  def buildAuthnCtx(data: Json, hmacData: HmacData): AuthnCtx = {
    val statusOpt = data.asObject.flatMap(o => o.toMap.get("status"))

    CloudentityAuthnCtx(
      userUuid = Some(hmacData.uuid),
      customerId = data.asObject.flatMap(o => o.toMap.get("cid").flatMap(_.asString)),
      realm = hmacData.realm,
      custom = statusOpt.map(j => Map("status" -> j))
    ).toCtx
  }

  override def tokenType(): VxFuture[String] = VxFuture.succeededFuture("hmac")
}

sealed trait HmacError
final case class LLAClientError[E](error: ClientError[E]) extends HmacError
final case class MissingKey() extends HmacError
final case class InvalidKeyFormat(message: String) extends HmacError
final case class KeyDecryptionError(message: String) extends HmacError
final case class ValidationError(error: Error) extends HmacError

case class HmacData(authorizationHeader: String, date: String, uuid: String, signature: String, realm: Option[String])

trait BaseHmacProviderConf {
  def authorization: String
  def date: String
  def key: String
  def authHeaderPrefix: String
  def dateFormat: String
  def limitInMinutes: Int
  def realm: Option[String]
  def apiKeyEncryptionKey: Option[String]
}
