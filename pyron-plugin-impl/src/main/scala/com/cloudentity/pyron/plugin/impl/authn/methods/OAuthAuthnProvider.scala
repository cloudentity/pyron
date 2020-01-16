package com.cloudentity.pyron.plugin.impl.authn.methods

import java.util.Optional

import com.nimbusds.jwt.JWTClaimsSet
import io.circe.parser.parse
import io.circe.Json
import com.cloudentity.pyron.api.Responses.Errors
import com.cloudentity.pyron.domain.flow._
import com.cloudentity.pyron.plugin.impl.authn.AuthnPlugin._
import com.cloudentity.pyron.plugin.impl.authn.{OidcClient, _}
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.{Future => VxFuture}
import com.cloudentity.pyron.domain.authn.CloudentityAuthnCtx
import com.cloudentity.pyron.domain.flow.AuthnCtx
import com.cloudentity.pyron.domain.http.TargetRequest
import io.vertx.core.json.JsonObject

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scalaz.{-\/, \/, \/-}

abstract class AbstractOAuthAuthnProvider extends ScalaServiceVerticle with AuthnProvider with OAuthHelper {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  var oidcClient: OidcClient = _

  override def initService(): Unit = {
    oidcClient = createClient(classOf[OidcClient], Optional.ofNullable(Option(getConfig()).getOrElse(new JsonObject()).getString("oidcId")))
  }

  override def start(start: VxFuture[Void]): Unit = super.start(start)

  def buildAuthnCtx(ctx: TracingContext, payload: JWTClaimsSet): AuthnCtx

  override def authenticate(ctx: RequestCtx, methodConf: AuthnMethodConf): VxFuture[Option[AuthnProviderResult]] =
    extractToken(ctx.request, methodConf) match {
      case Some(token) =>
        decodeAccessToken(ctx.tracingCtx, token).map(Option.apply).toJava()
      case None =>
        log.debug(ctx.tracingCtx, "Token is missing in the request")
        VxFuture.succeededFuture(None)
    }

  private def decodeAccessToken(ctx: TracingContext, token: String): Future[AuthnProviderResult] =
    oidcClient.getPublicKeys().toScala()
      .map {
        case \/-(keySet) =>
          OAuthAccessToken.parse(token, keySet) match {
            case Right(payload) =>
              log.debug(ctx, s"JWT verified successfully, payload: ${payload}")
              AuthnSuccess(buildAuthnCtx(ctx, payload).updated("token", Json.fromString(token)))
            case Left(ex) =>
              log.debug(ctx, s"JWT verification failed", ex)
              AuthnFailure(Errors.unauthenticated.toApiResponse)
          }
        case -\/(ex) =>
          log.error(ctx, s"Failed to get JWK keys", ex)
          AuthnFailure(Errors.unexpected.toApiResponse)
      }

  override def vertxServiceAddressPrefixS: Option[String] = Option(verticleId())

  override def tokenType(): VxFuture[String] = VxFuture.succeededFuture("accessTokenOAuth2")
}

trait OAuthHelper {
  def extractAuthorizationToken(req: TargetRequest, authorizationHeader: String, authorizationType: String): Option[String] =
    req.headers.getValues(authorizationHeader)
      .flatMap(_.find(_.startsWith(authorizationType)))
      .map(_.replaceFirst(s"$authorizationType ", ""))

  def extractToken(req: TargetRequest, methodConf: AuthnMethodConf): Option[String] =
    extractAuthorizationToken(req, "X-CE-OAUTH-TOKEN", "Bearer")
      .orElse(extractAuthorizationToken(req, methodConf.tokenHeader.getOrElse("Authorization"), "Bearer"))
}

class OAuthAuthorizationCodeAuthnProvider extends AbstractOAuthAuthnProvider with AuthnProvider {
  val ACCESS_TOKEN_USER_UUID_KEY = "sub"

  def buildAuthnCtx(tracingCtx: TracingContext, payload: JWTClaimsSet): AuthnCtx = {
    val userUuidOpt: Option[String] =
      Option(payload.getClaim(ACCESS_TOKEN_USER_UUID_KEY))
        .map(_.toString)

    val jwt = parse(payload.toJSONObject.toJSONString)
    val ctx = CloudentityAuthnCtx(userUuid = userUuidOpt, custom = Some(jwt.toTry.get.asObject.get.toMap)).toCtx

    log.debug(tracingCtx, s"Built AuthnCtx: $ctx")
    ctx
  }

  override def authenticate(ctx: RequestCtx, methodConf: AuthnMethodConf): VxFuture[Option[AuthnProviderResult]] =
    super.authenticate(ctx, methodConf)
}
