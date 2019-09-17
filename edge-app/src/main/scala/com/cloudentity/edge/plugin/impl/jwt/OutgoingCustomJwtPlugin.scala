package com.cloudentity.edge.plugin.impl.jwt

import io.circe._
import com.cloudentity.edge.plugin.config._
import com.cloudentity.edge.domain._
import com.cloudentity.edge.domain.flow.{AuthnCtx, PluginName, RequestCtx}
import com.cloudentity.edge.jwt.{JwtMapping, JwtService, JwtToken}
import com.cloudentity.edge.plugin.RequestPluginService
import com.cloudentity.edge.plugin.verticle.RequestPluginVerticle
import com.cloudentity.edge.util.ConfigDecoder
import com.cloudentity.tools.vertx.scala.FutureConversions
import com.cloudentity.tools.vertx.scala.VertxExecutionContext

import scala.concurrent.Future
import scala.util.{Failure, Success}

case class CustomJwtPluginConf(mapping: Json, defaults: Option[JsonObject], jwtServiceAddress: Option[String])

class OutgoingCustomJwtPlugin extends RequestPluginVerticle[CustomJwtPluginConf] with RequestPluginService with ConfigDecoder {
  import Codecs._
  import io.circe.generic.auto._
  import io.circe.generic.semiauto._

  override def name = PluginName("outgoingCustomJwt")
  override def confDecoder: Decoder[CustomJwtPluginConf] = deriveDecoder

  var jwtConfig: DefaultJwtPluginConf = _
  var jwtServiceClients: JwtServices = JwtServices()

  override def initService(): Unit = {
    jwtConfig = decodeConfigUnsafe[DefaultJwtPluginConf]
    jwtServiceClients = jwtServiceClients.add(vertx, jwtConfig.jwtServiceAddress).get
  }

  override def apply(ctx: RequestCtx, conf: CustomJwtPluginConf): Future[RequestCtx] = for {
    service       <- jwtServiceClients.get(conf.jwtServiceAddress.getOrElse(jwtConfig.jwtServiceAddress))
    ctxWithToken  <- OutgoingCustomJwtPlugin.buildRequestWithJwtToken(ctx, conf)(jwtConfig, service)
  } yield ctxWithToken

  override def validate(conf: CustomJwtPluginConf): ValidateResponse = conf.jwtServiceAddress match {
    case Some(address) => jwtServiceClients.add(vertx, address) match {
      case Success(clients) =>
        jwtServiceClients = clients
        ValidateOk
      case Failure(error) =>
        ValidateError(s"Failed to create $address JwtService for OutgoingDefaultJwtPlugin: ${error.getMessage}")
    }
    case None => ValidateOk
  }
}

object OutgoingCustomJwtPlugin extends FutureConversions {
  def buildRequestWithJwtToken(ctx: RequestCtx, conf: CustomJwtPluginConf)
                              (jwtConfig: DefaultJwtPluginConf, client: JwtService)
                              (implicit ex: VertxExecutionContext): Future[RequestCtx] = for {
    emptyToken <- client.empty().toScala()
    remapped    = remappedClaims(emptyToken, ctx.authnCtx, conf)
    encoded    <- client.sign(emptyToken.withClaims(remapped)).toScala()
    authHeaderName = jwtConfig.authHeader.flatMap(_.name).getOrElse("Authorization")
    authHeaderValue = jwtConfig.authHeader.flatMap(_.pattern).getOrElse("Bearer {}").replace("{}", encoded)
  } yield ctx.modifyRequest(_.modifyHeaders(_.set(authHeaderName, authHeaderValue)))

  def remappedClaims(token: JwtToken, ctx: AuthnCtx, conf: CustomJwtPluginConf): Json = {
    val refs: Json = token.withCtxClaims(ctx).claims
    JwtMapping.updateWithRefs(conf.mapping, refs, conf.defaults.getOrElse(JsonObject.empty))
  }
}
