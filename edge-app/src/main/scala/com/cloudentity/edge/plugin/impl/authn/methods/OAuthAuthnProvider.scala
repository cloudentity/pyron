package com.cloudentity.edge.plugin.impl.authn.methods

import com.nimbusds.jwt.JWTClaimsSet
import io.circe.parser.parse
import io.circe.{Json, JsonObject}
import com.cloudentity.edge.api.Responses.Errors
import com.cloudentity.edge.domain.flow._
import com.cloudentity.edge.jwt.OAuthAccessToken
import com.cloudentity.edge.plugin.impl.authn.AuthnPlugin._
import com.cloudentity.edge.plugin.impl.authn._
import com.cloudentity.edge.service.OidcClient
import com.cloudentity.tools.vertx.http.builder.SmartHttpResponse
import com.cloudentity.tools.vertx.http.{SimpleSmartHttpClient, SmartHttpClient}
import com.cloudentity.tools.vertx.jwt.api.JwtService
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.scala.{FutureConversions, ScalaSyntax}
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.buffer.Buffer
import io.vertx.core.{Future => VxFuture}
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import com.cloudentity.edge.commons.ClientWithTracing._
import com.cloudentity.edge.domain.authn.CloudentityAuthnCtx
import com.cloudentity.edge.domain.flow.AuthnCtx
import com.cloudentity.edge.domain.http.TargetRequest

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scalaz.{-\/, \/, \/-}

abstract class AbstractOAuthAuthnProvider extends ScalaServiceVerticle with AuthnProvider with OAuthHelper {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  var oidcClient: OidcClient = _

  override def initService(): Unit = {
    oidcClient = createClient(classOf[OidcClient])
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
              log.debug(ctx, s"Jwt verified successfully, payload: ${payload}")
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

class OAuthClientCredentialsAuthnProvider extends AbstractOAuthAuthnProvider with AuthnProvider {
  val ACCESS_TOKEN_CLIENT_ID_KEY = "sub"

  def buildAuthnCtx(tracingCtx: TracingContext, payload: JWTClaimsSet): AuthnCtx = {
    val clientIdOpt: Option[String] =
      Option(payload.getClaim(ACCESS_TOKEN_CLIENT_ID_KEY))
        .map(_.toString)

    val ctx = CloudentityAuthnCtx(oAuthClientId = clientIdOpt).toCtx
    log.debug(tracingCtx, s"Built AuthnCtx: $ctx")
    ctx
  }

  override def authenticate(ctx: RequestCtx, methodConf: AuthnMethodConf): VxFuture[Option[AuthnProviderResult]] =
    super.authenticate(ctx, methodConf)
}

class OAuthAuthorizationCodeIntrospectionAuthnProvider extends ScalaServiceVerticle with AuthnProvider {

  var introspection: OAuthAuthorizationCodeIntrospection = _

  override def initService(): Unit = {
    val jwtService = createClient(classOf[JwtService])
    val oidcClient = SimpleSmartHttpClient.create(vertx, getConfig.getJsonObject("http")).toTry.map { client =>
      new OAuthTokenIntrospectClientImpl(client, Option(getConfig.getString("introspectPath")).getOrElse("/api/introspect"))
    }.get

    introspection = new OAuthAuthorizationCodeIntrospection(jwtService, oidcClient)
  }

  override def authenticate(ctx: RequestCtx, methodConf: AuthnMethodConf): VxFuture[Option[AuthnProviderResult]] =
    introspection.authenticate(ctx, methodConf)

  override def vertxServiceAddressPrefixS: Option[String] = Option(verticleId())

  override def tokenType(): VxFuture[String] = VxFuture.succeededFuture("accessTokenOAuth2")
}

class OAuthAuthorizationCodeIntrospection(jwtService: JwtService, oidcClient: OAuthTokenIntrospectClient)(implicit ec: VertxExecutionContext) extends AuthnProvider with OAuthHelper with FutureConversions with ScalaSyntax {

  sealed trait TokenIntrospectResult
    case class Content(value: JsonObject) extends TokenIntrospectResult
    case object TokenInactive extends TokenIntrospectResult

  override def authenticate(ctx: RequestCtx, methodConf: AuthnMethodConf): VxFuture[Option[AuthnProviderResult]] = {
    extractToken(ctx.request, methodConf) match {
      case Some(oauthToken) =>
        val program: Future[Throwable \/ Option[AuthnProviderResult]] = {
          for {
            jwt          <- jwtService.sign().toScala().map(toEither).toOperation
            responseBody <- oidcClient.introspect(ctx.tracingCtx, oauthToken, jwt).toOperation
            authnResult   = if (isTokenActive(responseBody))
                              responseBody.remove("active") |> NormalizeScope.splitScope |> toAuthnCtx |> (_.updated("token", Json.fromString(oauthToken))) |> AuthnSuccess.apply
                            else
                              AuthnFailure(Errors.unauthenticated.toApiResponse, Modify.noop)
          } yield Some(authnResult)
        }.run

        program.flatMap {
          case \/-(authnResult) => Future.successful(authnResult)
          case -\/(ex)          => Future.failed(ex)
        }.toJava()

      case None =>
        VxFuture.succeededFuture(None)
    }
  }

  private def isTokenActive(content: JsonObject): Boolean =
    content.toMap.get("active").flatMap(_.asBoolean).getOrElse(false)

  private def toAuthnCtx(content: JsonObject): AuthnCtx =
    CloudentityAuthnCtx(
      userUuid = content("sub").flatMap(_.asString),
      custom   = Some(content.toMap)
    ).toCtx

  private def toEither[A, B](e: io.vavr.control.Either[A, B]): A \/ B =
    if (e.isLeft) e.getLeft.left else e.get.right

  override def tokenType(): VxFuture[String] = VxFuture.succeededFuture("accessTokenOAuth2")
}

trait OAuthTokenIntrospectClient {
  def introspect(tracing: TracingContext, oauthToken: String, jwt: String): Future[Throwable \/ JsonObject]
}

class OAuthTokenIntrospectClientImpl(client: SmartHttpClient, introspectPath: String)
                                    (implicit ec: VertxExecutionContext)
  extends OAuthTokenIntrospectClient
    with OAuthHelper
    with FutureConversions
    with ScalaSyntax {

  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  def introspect(tracing: TracingContext, oauthToken: String, jwt: String): Future[Throwable \/ JsonObject] = {
    for {
      resp          <- callOidcService(tracing, oauthToken, jwt).toOperation
      body          <- validateOidcResponse(resp).toOperation
      json          <- parseJson(body).toOperation
      jsonObject    <- asJsonObject(json).toOperation
    } yield jsonObject
  }.run

  private def validateOidcResponse(resp: SmartHttpResponse): Throwable \/ Buffer =
    if (resp.getHttp.statusCode() == 200) resp.getBody.right
    else new Exception("Non-200 response from OIDC Service").left

  private def callOidcService(tracing: TracingContext, oauthToken: String, jwt: String): VxFuture[SmartHttpResponse] = {
    client
      .post(s"$introspectPath?token=$oauthToken")
      .putHeader("Authorization", s"Bearer $jwt")
      .withTracing(tracing)
      .endWithBody(tracing)
  }

  private def parseJson(body: Buffer): Throwable \/ Json =
    \/.fromEither(parse(body.toString))

  private def asJsonObject(json: Json): Throwable \/ JsonObject =
    json.asObject match {
      case Some(obj) => obj.right
      case None      => new Exception("Token introspection did not return JsonObject").left
    }
}

object NormalizeScope {
  /**
    * Splitting scopes from OIDC represented as String into an array of Strings, e.g.:
    * { "scope": "create update" } ==> { "scope": [ "create", "update" ] }
    */
  def splitScope(obj: JsonObject): JsonObject = {
    val splitScope: Option[Json] = obj("scope").flatMap(_.asString).map(string => Json.arr(string.split("\\s").map(Json.fromString):_*))
    splitScope.fold(obj) { scope => obj.add("scope", scope) }
  }
}
