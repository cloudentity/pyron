package com.cloudentity.edge.plugin.impl.authn.methods

import io.circe.{Decoder, Json, JsonObject}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode
import com.cloudentity.edge.api.Responses
import com.cloudentity.edge.api.Responses.Errors
import com.cloudentity.edge.commons.ResponseHelper
import com.cloudentity.edge.cookie.{CookieSettings, CookieUtils}
import com.cloudentity.edge.domain.http
import com.cloudentity.edge.jwt.{JwtService, JwtServiceFactory}
import com.cloudentity.edge.plugin.impl.authn.AuthnPlugin._
import com.cloudentity.edge.plugin.impl.authn.{AuthnMethodConf, AuthnProvider}
import com.cloudentity.edge.plugin.impl.session.SessionServiceConf
import com.cloudentity.edge.service.session.SessionServiceClient.Session
import com.cloudentity.edge.service.session.{OrchisToken, SessionServiceClient, SessionServiceHelper}
import com.cloudentity.edge.util.ConfigDecoder
import com.cloudentity.tools.vertx.http.{SimpleSmartHttpClient, SmartHttpClient}
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.buffer.Buffer
import io.vertx.core.{Future => VxFuture}

import scala.concurrent.Future
import com.cloudentity.edge.commons.ClientWithTracing._
import com.cloudentity.edge.domain.authn.CloudentityAuthnCtx
import com.cloudentity.edge.domain.flow.{AuthnCtx, RequestCtx}
import com.cloudentity.edge.domain.http.{ApiResponse, TargetRequest}
import com.cloudentity.tools.vertx.http.Headers

trait SsoAuthnProviderConf {
  def sessionService: SessionServiceConf
  def tokenHeaderName: Option[String]
  def tokenCookieName: Option[String]
  def tokenCookieSettings: Option[CookieSettings]
  def csrfTokenHeader: Option[String]
  def userIdName: String
}

case class CloudSsoAuthnProviderConf(
  sessionService: SessionServiceConf,
  tokenHeaderName: Option[String],
  tokenCookieName: Option[String],
  tokenCookieSettings: Option[CookieSettings],
  csrfTokenHeader: Option[String],
  userIdName: String,
  jwtServiceAddress: String
) extends SsoAuthnProviderConf

case class EdgeSsoAuthnProviderConf(
  sessionService: SessionServiceConf,
  tokenHeaderName: Option[String],
  tokenCookieName: Option[String],
  tokenCookieSettings: Option[CookieSettings],
  csrfTokenHeader: Option[String],
  userIdName: String,
  getSessionPath: Option[String],
  getSessionTokenHeader: Option[String]
) extends SsoAuthnProviderConf

/**
  * Authenticate returns:
  * {
  *   "userUuid": ${session.uuid},
  *   "deviceUuid": ${session.deviceUuid},
  *   "session": ${session},
  *   "token": ${request.token}
  * }
  */
abstract class BaseSsoAuthnProvider extends ScalaServiceVerticle with AuthnProvider with ResponseHelper with ConfigDecoder {
  override val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  val SESSION_DEVICE_UUID_KEY = "deviceUuid"

  def ssoConf: SsoAuthnProviderConf
  def callGetSession(ctx: RequestCtx, token: String): Future[Option[JsonObject]]

  implicit lazy val CookieSettingsDec = deriveDecoder[CookieSettings]
  implicit lazy val SessionServiceConfDec = deriveDecoder[SessionServiceConf]

  override def vertxServiceAddressPrefixS: Option[String] = Option(verticleId())

  override def authenticate(ctx: RequestCtx, methodConf: AuthnMethodConf): VxFuture[Option[AuthnProviderResult]] =
    readToken(ctx.tracingCtx, ctx.request, methodConf) match {
      case Right(Some(token)) => retrieveSession(ctx, token, methodConf.tokenHeader).map(Option.apply).toJava()
      case Right(None) =>
        log.debug(ctx.tracingCtx, "Token is missing in the request")
        VxFuture.succeededFuture(None)
      case Left(err) => VxFuture.succeededFuture(Some(AuthnFailure(err, Modify.noop)))
    }

  private def readToken(ctx: TracingContext, req: TargetRequest, methodConf: AuthnMethodConf): Either[ApiResponse, Option[String]] = {
    log.debug(ctx, s"Request headers: ${req.headers}")
    readTokenFromHeader(req, methodConf.tokenHeader) match {
      case Some(token) => Right(Some(token))
      case None => readTokenFromCookie(ctx, req, methodConf.tokenHeader) match {
        case Some(_) if csrfFilterIsOn && !requestContainsCSRF(req) => Left(http.ApiResponse(400, Buffer.buffer(Responses.mkString(Errors.invalidCSRFHeader.body)), Headers.of("Content-Type" -> "application/json")))
        case Some(cookieToken) if !csrfFilterIsOn || requestContainsCSRF(req) => Right(Some(cookieToken))
        case None => Right(None)
      }
    }
  }

  private def csrfFilterIsOn(): Boolean = !ssoConf.csrfTokenHeader.isEmpty

  private def requestContainsCSRF(req: TargetRequest): Boolean = req.headers.contains(ssoConf.csrfTokenHeader.get)

  private def readTokenFromHeader(req: TargetRequest, tokenHeaderNameOpt: Option[String]): Option[String] =
    tokenHeaderNameOpt.orElse(ssoConf.tokenHeaderName).flatMap(req.headers.get)
      .flatMap { headerValue =>
        // read token only if it has 'sso xyz' form or does not have any prefix (excludes 'Bearer xyz')
        headerValue.split(" ").toList match {
          case prefix :: token if prefix.toLowerCase == "sso" => Some(token.mkString(" "))
          case token :: Nil                                   => Some(token)
          case _                                              => None
        }
      }

  private def readTokenFromCookie(ctx: TracingContext, req: TargetRequest, tokenHeaderNameOpt: Option[String]): Option[String] = {
    log.debug(ctx, s"cookie header: ${req.headers.get("Cookie")}")
    for {
      cookieName <- tokenHeaderNameOpt.orElse(ssoConf.tokenCookieName)
      cookiesMap <- req.headers.getValues("Cookie").map(CookieUtils.parseCookies)
      token      <- cookiesMap.get(cookieName)
    } yield token
  }

  def decodeAndValidateConfigUnsafe[A <: SsoAuthnProviderConf](implicit d: Decoder[A]): A = {
    val conf = decodeConfigUnsafe[A]
    if (conf.tokenHeaderName.isEmpty && conf.tokenCookieName.isEmpty)
      throw new Exception("At least one of 'tokenHeaderName' and 'tokenCookieName' must be set")
    else conf
  }

  private def retrieveSession(ctx: RequestCtx, token: String, tokenHeaderNameOpt: Option[String]): Future[AuthnProviderResult] =
    callGetSession(ctx, token)
      .map[AuthnProviderResult] {
        case Some(session) =>
          log.debug(ctx.tracingCtx, s"Got $session for $ctx")
          AuthnSuccess(buildAuthnCtx(ctx.tracingCtx, session, token))
        case None =>
          val modify = removeSessionCookieIfProvided(ctx, tokenHeaderNameOpt)
          log.debug(ctx.tracingCtx, s"${if (modify.isDefined) "Removing" else "Not removing"} client cookie '${ssoConf.tokenCookieName}'")
          AuthnFailure(Errors.unauthenticated.toApiResponse, modify.getOrElse(Modify.noop))
      }

  private def removeSessionCookieIfProvided(ctx: RequestCtx, tokenHeaderNameOpt: Option[String]): Option[Modify] =
    for {
      _        <- readTokenFromCookie(ctx.tracingCtx, ctx.request, tokenHeaderNameOpt)
      name     <- ssoConf.tokenCookieName
      settings <- ssoConf.tokenCookieSettings
      removeCookie: (ApiResponse => ApiResponse) = removeClientCookie(name, settings, _)
    } yield Modify(_.withModifyResponse(removeCookie), removeCookie)

  private def buildAuthnCtx(tracingCtx: TracingContext, session: Session, token: String): AuthnCtx = {
    val sessionMap = session.toMap

    val ctx = CloudentityAuthnCtx(
      userUuid = sessionMap.get(ssoConf.userIdName).flatMap(_.asString),
      deviceUuid = sessionMap.get(SESSION_DEVICE_UUID_KEY).flatMap(_.asString),
      session = Some(Json.fromJsonObject(session)),
      customerId = session("customer").flatMap(_.asString),
      custom  = Some(Map("token" -> Json.fromString(token)))
    ).toCtx

    log.debug(tracingCtx, s"Built AuthnCtx: $ctx")
    ctx
  }

  override def tokenType(): VxFuture[String] = VxFuture.succeededFuture("sso")
}

class SsoAuthnProvider extends BaseSsoAuthnProvider with SessionServiceHelper {
  var jwtServiceClient: JwtService = _
  var client: SessionServiceClient = _
  var ssoConf: SsoAuthnProviderConf = _

  implicit lazy val SsoAuthnProviderConfDec = deriveDecoder[CloudSsoAuthnProviderConf]

  override def initService() = {
    val conf = decodeAndValidateConfigUnsafe[CloudSsoAuthnProviderConf]

    ssoConf = conf
    jwtServiceClient = JwtServiceFactory.createClient(vertx, conf.jwtServiceAddress)
    client = SessionServiceClient(vertx, ssoConf.sessionService, jwtServiceClient)
  }

  def callGetSession(ctx: RequestCtx, token: String): Future[Option[SessionServiceClient.Session]] =
    getSession(ctx.tracingCtx, ctx.request, Some(OrchisToken(token)), jwtServiceClient, "token")
}

class EdgeSsoAuthnProvider extends BaseSsoAuthnProvider {
  val DEFAULT_GET_SESSION_PATH = "/session"
  val DEFAULT_GET_SESSION_TOKEN_HEADER = "token"
  var getSessionPath: String = _
  var getSessionTokenHeader: String = _

  var smartClient: SmartHttpClient = _
  var ssoConf: SsoAuthnProviderConf = _


  implicit lazy val SsoAuthnProviderConfDec = deriveDecoder[EdgeSsoAuthnProviderConf]

  override def initService() = {
    val conf = decodeAndValidateConfigUnsafe[EdgeSsoAuthnProviderConf]

    ssoConf = conf
    getSessionPath = conf.sessionService.path + conf.getSessionPath.getOrElse(DEFAULT_GET_SESSION_PATH)
    getSessionTokenHeader = conf.getSessionTokenHeader.getOrElse(DEFAULT_GET_SESSION_TOKEN_HEADER)
    smartClient = SimpleSmartHttpClient.create(vertx, ssoConf.sessionService.host, ssoConf.sessionService.port, ssoConf.sessionService.ssl, "")
  }

  override def callGetSession(ctx: RequestCtx, token: String): Future[Option[SessionServiceClient.Session]] =
    smartClient.get(getSessionPath)
      .putHeader(getSessionTokenHeader, token)
      .withTracing(ctx.tracingCtx)
      .endWithBody(ctx.tracingCtx).toScala()
      .flatMap { response =>
        if (response.getHttp.statusCode() == 200)
          Future.fromTry(decode[JsonObject](response.getBody.toString()).toTry).map(Option.apply)
        else Future.successful(None)
      }
}
