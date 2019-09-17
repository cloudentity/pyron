package com.cloudentity.edge.plugin.impl.authn.methods.oauth10

import io.circe.generic.auto._
import io.circe.parser._
import io.circe.{Decoder, Json}
import com.cloudentity.edge.api.Responses
import com.cloudentity.edge.domain.flow.{AuthnCtx, RequestCtx}
import com.cloudentity.edge.plugin.impl.authn.AuthnPlugin.{AuthnFailure, AuthnProviderResult, AuthnSuccess}
import com.cloudentity.edge.plugin.impl.authn.methods.oauth10.loader._
import com.cloudentity.edge.plugin.impl.authn.methods.oauth10.nonce.{HazelcastNonceValidator, NonceNotUnique, NonceValidator}
import com.cloudentity.edge.plugin.impl.authn.methods.oauth10.signer.{SignatureVerifier, SignatureVerifierMismatch, _}
import com.cloudentity.edge.plugin.impl.authn.{AuthnMethodConf, AuthnPlugin, AuthnProvider}
import com.cloudentity.edge.util.ConfigDecoder
import com.cloudentity.tools.vertx.futures.FutureUtils
import com.cloudentity.tools.vertx.scala.Operation
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import com.cloudentity.tools.vertx.verticles.VertxDeploy
import io.vertx.core.buffer.Buffer
import io.vertx.core.{Future => VxFuture}
import scalaz.{-\/, \/, \/-}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.util.matching.Regex

case class PublicDomainConf(host: String, port: Int, ssl: Boolean)
case class HeaderConf(name: String, valueRegexp: Regex)

/**
  * See adoc for configuration details
  */
case class OAuth10AuthnProviderConf(header: HeaderConf, publicDomain: PublicDomainConf, skewTimeInSeconds: Option[Int],
                                    signingKeyLoader: Option[String], signatureVerifiers: Option[Map[String, String]],
                                    nonceValidator: Option[String], consumerKeyRegex: Option[Regex])

case class OAuth10Request(consumerKey: String, token: Option[String], signature: String, signatureMethod: String,
                          timestamp: Long, nonce: String, version: Option[String])

sealed trait OAuth10AuthnProviderError
  sealed trait InvalidRequest extends OAuth10AuthnProviderError
    case class InvalidParamFormat(param: String) extends InvalidRequest
    case class MissingParam(param: String) extends InvalidRequest
    case object UnsupportedSignatureMethod extends InvalidRequest
    case object InvalidNonce extends InvalidRequest
    case class MalformedHeader(ex: Throwable) extends InvalidRequest

  sealed trait Unauthenticated extends OAuth10AuthnProviderError
    case object InvalidTimestamp extends Unauthenticated
    case object InvalidSignature extends Unauthenticated
    case object KeyNotFound extends Unauthenticated
  case class InternalError(ex: Throwable) extends OAuth10AuthnProviderError


/**
  * See adoc for implementation details
  */
class OAuth10AuthnProvider extends ScalaServiceVerticle with AuthnProvider with ConfigDecoder  {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  var conf: OAuth10AuthnProviderConf = _

  val defaultKeyLoader = classOf[ConfigRsaSigningKeyLoader].getName
  val defaultSignatureMethods = Map("RSA-SHA256" -> classOf[RsaPublicKeySignatureVerifier].getName)
  val defaultSkewTimeInSeconds = 15
  val defaultConsumerKeyRegex = "(.*)".r
  val defaultNonceValidator = classOf[HazelcastNonceValidator].getName
  var signatureVerifierClients: Map[String, SignatureVerifier] = _

  var keyLoaderClient: SigningKeyLoader = _
  var nonceValidatorClient: NonceValidator = _

  val charset = "UTF-8"

  implicit val regexDecoder = Decoder.decodeString.emapTry { regexp => Try(regexp.r) }

  override def vertxServiceAddressPrefixS: Option[String] = Option(verticleId())

  override def initServiceAsyncS(): Future[Unit] = {
    conf = decodeConfigUnsafe[OAuth10AuthnProviderConf]

    val signatureVerifiers = conf.signatureVerifiers.getOrElse(defaultSignatureMethods)

    keyLoaderClient = createClient(classOf[SigningKeyLoader])
    nonceValidatorClient = createClient(classOf[NonceValidator])

    signatureVerifierClients = signatureVerifiers.map {
      case (signature, _) => (signature, createClient(classOf[SignatureVerifier], signature))
    }

    val signatureVerifiersFutures = signatureVerifiers.map {
      case (verticleId, verticle) => VertxDeploy.deploy(vertx, verticle, verticleId)
    }.toList.asJava

    VertxDeploy.deploy(vertx, conf.signingKeyLoader.getOrElse(defaultKeyLoader))
      .compose(_ => FutureUtils.sequence(signatureVerifiersFutures))
      .compose(_ => VertxDeploy.deploy(vertx, conf.nonceValidator.getOrElse(defaultNonceValidator)))
      .toScala().map(_ => ())
  }

  /**
    * If TargetRequest contains all the required attribute to perform authentication
    * then it should return Future[Some[AuthnProviderResult]]. Otherwise Future[None]
    */
  override def authenticate(req: RequestCtx, methodConf: AuthnMethodConf): VxFuture[Option[AuthnPlugin.AuthnProviderResult]] = {
    getOAuthHeaderParams(req, methodConf) match {
      case Some(paramsStr) =>
        val program: Operation[OAuth10AuthnProviderError, Unit] = for {
          params <- parseParams(req.tracingCtx, paramsStr).toOperation
          _ = log.debug(req.tracingCtx, s"params: ${params}")
          _ <- validateRequest(req, params)
        } yield {}

        program.run.map[AuthnProviderResult] {
          case \/-(_) =>
            val attributes = AuthnCtx()
            AuthnSuccess(attributes)
          case -\/(ex) =>
            val error = ex match {
              case MissingParam(param) =>
                val msg = s"Missing param: ${param}"
                log.error(req.tracingCtx, msg)
                Responses.Errors.invalidRequest(msg)
              case InvalidParamFormat(param) =>
                val msg = s"Invalid param format: ${param}"
                log.error(req.tracingCtx, msg)
                Responses.Errors.invalidRequest(msg)
              case MalformedHeader(ex) =>
                val msg = s"Malformed header: ${ex}"
                log.error(req.tracingCtx, msg)
                Responses.Errors.invalidRequest
              case InvalidNonce =>
                val msg = "Invalid nonce"
                log.error(req.tracingCtx, msg)
                Responses.Errors.invalidRequest(msg)
              case UnsupportedSignatureMethod =>
                val msg = "Unsupported signature method"
                log.error(req.tracingCtx, msg)
                Responses.Errors.invalidRequest(msg)
              case KeyNotFound =>
                log.error(req.tracingCtx, s"Key not found")
                Responses.Errors.unauthenticated
              case InvalidTimestamp =>
                log.error(req.tracingCtx, s"Invalid timestamp")
                Responses.Errors.unauthenticated
              case InvalidSignature =>
                log.error(req.tracingCtx, s"Invalid Signature")
                Responses.Errors.unauthenticated
              case InternalError(ex) =>
                log.error(req.tracingCtx, s"Internal error", ex)
                Responses.Errors.unexpected
            }
            AuthnFailure(error.toApiResponse())
          }.map(Option(_)).toJava()

      case None =>
        VxFuture.succeededFuture(None)
    }
  }

  def validateRequest(request: RequestCtx, params: OAuth10Request): Operation[OAuth10AuthnProviderError, Unit] = {
    for {
      _ <- validateVersion(params).toOperation
      verifier <- validateAndGetSignatureVerifier(params).toOperation
      _ <- validateTimestamp(params).toOperation
      _ <- nonceValidatorClient.validate(params).toOperation.leftMap[OAuth10AuthnProviderError](ex => ex match {
        case NonceNotUnique => InvalidNonce
      })
      _ <- validateSignature(request.tracingCtx, verifier, params, request)
    } yield ()
  }

  def validateVersion(params: OAuth10Request): OAuth10AuthnProviderError \/ Unit = {
    params.version match {
      case Some(v) => v match {
        case "1.0" => \/-(())
        case _ => -\/(InvalidParamFormat("oauth_version"))
      }
      case None => \/-(())
    }
  }

  def validateAndGetSignatureVerifier(params: OAuth10Request): OAuth10AuthnProviderError \/ SignatureVerifier = {
    signatureVerifierClients.get(params.signatureMethod) match {
      case Some(v) => \/-(v)
      case None => -\/(UnsupportedSignatureMethod)
    }
  }

  def validateTimestamp(params: OAuth10Request): OAuth10AuthnProviderError \/ Unit = {
    val now = System.currentTimeMillis() / 1000
    val skewTime = conf.skewTimeInSeconds.getOrElse(defaultSkewTimeInSeconds)
    val diff = Math.abs(now - params.timestamp)
    if (diff <= skewTime) \/-(())
    else {
      log.error(TracingContext.dummy(), s"Timestamp validation failed, diff: ${diff}, skewTime: ${skewTime}")
      -\/(InvalidTimestamp)
    }
  }

  def extractKeyId(params: OAuth10Request): OAuth10AuthnProviderError \/ String = {
    val regex = conf.consumerKeyRegex.getOrElse(defaultConsumerKeyRegex)
    regex.findFirstMatchIn(params.consumerKey).flatMap(v => Try(v.group(1)).toOption) match {
      case Some(keyId)=> \/-(keyId)
      case None => -\/(InvalidParamFormat("oauth_consumer_key"))
    }
  }

  def validateSignature(tracing: TracingContext, verifier: SignatureVerifier, params: OAuth10Request, ctx: RequestCtx): Operation[OAuth10AuthnProviderError, Unit] = {
    def loadKey(keyId: String): Operation[OAuth10AuthnProviderError, Array[Byte]] = {
      log.debug(tracing, s"Get public key by id: ${keyId}")
      keyLoaderClient.load(keyId).toOperation.leftMap[OAuth10AuthnProviderError](ex => ex match {
        case KeyLoaderKeyNotFound() => KeyNotFound
        case KeyLoaderFailure(ex) => InternalError(ex)
      })
    }

    def verify(key: Array[Byte], baseString: String): Operation[OAuth10AuthnProviderError, Unit] = {
      verifier.verify(params.signature.getBytes(charset), baseString.getBytes(charset), key)
        .toOperation.leftMap[OAuth10AuthnProviderError](ex => ex match {
        case SignatureVerifierMismatch => InvalidSignature
        case SignatureVerifierFailure(ex) => InternalError(ex)
      })
    }

    def calculateRequestBaseString(): Operation[OAuth10AuthnProviderError, String] = {
      OAuth10SignatureBaseStringBuilder.build(ctx.tracingCtx, ctx.original, params, conf.publicDomain).toOperation.leftMap[OAuth10AuthnProviderError](ex => ex match {
        case OAuth10SignatureBaseStringBuilderFailure(ex) => InternalError(ex)
      })
    }

    for {
      keyId <- extractKeyId(params).toOperation
      key <- loadKey(keyId)
      baseString <- calculateRequestBaseString()
      _ <- verify(key, baseString)
    } yield ()
  }

  def parseParams(ctx: TracingContext, params: String): OAuth10AuthnProviderError \/ OAuth10Request = {
    OAuth10HeaderParser.parse(params) match {
      case Success(paramsMap) => for {
        consumerKey <- getOrMissingParam(paramsMap, "oauth_consumer_key")
        tokenOpt = paramsMap.get("oauth_token")
        signature <- getOrMissingParam(paramsMap, "oauth_signature")
        signatureMethod <- getOrMissingParam(paramsMap, "oauth_signature_method")
        timestamp <- getOrMissingParam(paramsMap, "oauth_timestamp")
        timestampSeconds <- \/.fromTryCatchNonFatal { timestamp.toLong }
          .leftMap[OAuth10AuthnProviderError](_ => InvalidParamFormat("oauth_timestamp"))
        nonce <- getOrMissingParam(paramsMap, "oauth_nonce")
        version <- \/-(paramsMap.get("oauth_version"))
      } yield OAuth10Request(consumerKey, tokenOpt, signature, signatureMethod, timestampSeconds, nonce, version)
      case Failure(ex) => -\/(MalformedHeader(ex))
    }
  }

  def getOrMissingParam(params: Map[String, String], param: String): OAuth10AuthnProviderError \/ String = {
    params.get(param) match {
      case Some(v) => \/-(v)
      case None => -\/(MissingParam(param))
    }
  }

  def getOAuthHeaderParams(req: RequestCtx, methodConf: AuthnMethodConf): Option[String] = {
    for {
      header <- req.request.headers.get(methodConf.tokenHeader.getOrElse(conf.header.name))
      regexMatch <- conf.header.valueRegexp.findFirstMatchIn(header)
      params <- Try(regexMatch.group(1)).toOption
    } yield (params)
  }

  override def tokenType(): VxFuture[String] = VxFuture.succeededFuture("accessTokenOAuth1")
}
