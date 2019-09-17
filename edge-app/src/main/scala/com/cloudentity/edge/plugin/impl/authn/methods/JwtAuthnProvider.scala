package com.cloudentity.edge.plugin.impl.authn.methods

import io.circe.{Json, JsonObject}
import com.cloudentity.edge.api.Responses.Errors
import com.cloudentity.edge.domain.flow.{AuthnCtx, RequestCtx}
import com.cloudentity.edge.domain.http.TargetRequest
import com.cloudentity.edge.domain.authn.CloudentityAuthnCtx
import com.cloudentity.edge.jwt.{JwtMapping, JwtService, JwtServiceFactory, JwtToken}
import com.cloudentity.edge.plugin.impl.authn.AuthnPlugin.{AuthnFailure, AuthnProviderResult, AuthnSuccess}
import com.cloudentity.edge.plugin.impl.authn.{AuthnMethodConf, AuthnProvider}
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.vertx.core.{Future => VxFuture}
import com.cloudentity.edge.plugin.impl.authn.codecs._
import com.cloudentity.edge.util.ConfigDecoder
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}

import scala.concurrent.Future
import scala.util.{Failure, Success}

case class JwtAuthnProviderConfig(jwtServiceAddress: String, mapping: Option[JsonObject])

class JwtAuthnProvider extends ScalaServiceVerticle with AuthnProvider with ConfigDecoder {
  val log = LoggingWithTracing.getLogger(this.getClass)

  var jwtServiceClient: JwtService = _
  var conf: JwtAuthnProviderConfig = _

  override def initService() = {
    conf = decodeConfigUnsafe[JwtAuthnProviderConfig]
    jwtServiceClient = JwtServiceFactory.createClient(vertx, conf.jwtServiceAddress)
  }

  override def vertxServiceAddressPrefixS: Option[String] = Option(verticleId())

  override def authenticate(ctx: RequestCtx, methodConf: AuthnMethodConf): VxFuture[Option[AuthnProviderResult]] =
    extractToken(ctx.request, methodConf) match {
      case Some(token) =>
        decodeJwt(ctx.tracingCtx, token).map(Option.apply).toJava()
      case None =>
        log.debug(ctx.tracingCtx, "Jwt is missing in the request")
        VxFuture.succeededFuture(None)
    }

  def buildAuthnCtx(token: JwtToken): Either[Throwable, AuthnCtx] =
    conf.mapping match {
      case None =>
        for {
          jsonOpt <- token.claims.hcursor.downField("content").as[Option[io.circe.JsonObject]]
        } yield CloudentityAuthnCtx.build(jsonOpt.getOrElse(io.circe.JsonObject.empty).toMap).toCtx
      case Some(mapping) =>
        token.claims.asObject.toRight(new Exception("JwtToken claims should be JSON object"))
          .map { claims =>
            val mappedJson = mapJwtClaims(claims, mapping)
            CloudentityAuthnCtx.build(mappedJson.toMap).toCtx
          }
    }

  private def mapJwtClaims(claims: JsonObject, mapping: JsonObject) =
      JwtMapping.updateWithRefs(Json.fromJsonObject(mapping), Json.fromJsonObject(claims))
        .asObject.get // we can call Option.get here, because we are passing JsonObject to JwtMapping.updateWithRefs

  private def decodeJwt(ctx: TracingContext, token: String): Future[AuthnProviderResult] =
    jwtServiceClient.parse(token).toScala().transform {
      case Success(claims) =>
        buildAuthnCtx(claims) match {
          case Right(ctx) =>
            Success(AuthnSuccess(ctx))
          case Left(err) =>
            log.error(ctx, s"Failed to build context", err)
            Success(AuthnFailure(Errors.unexpected.toApiResponse))
        }
      case Failure(err) =>
        log.error(ctx, s"Failed to parse jwt", err)
        Success(AuthnFailure(Errors.unauthenticated.toApiResponse))
    }

  private def extractToken(req: TargetRequest, methodConf: AuthnMethodConf): Option[String] =
    req.headers.get(methodConf.tokenHeader.getOrElse("Authorization")).map(_.replaceFirst("Bearer ", ""))

  override def tokenType(): VxFuture[String] = VxFuture.succeededFuture("jwt")
}
