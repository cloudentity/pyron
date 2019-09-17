package com.cloudentity.edge.plugin.impl.jwt

import io.circe._
import com.cloudentity.edge.plugin.config._
import com.cloudentity.edge.domain._
import com.cloudentity.edge.domain.flow.{PluginName, RequestCtx}
import com.cloudentity.edge.jwt.JwtService
import com.cloudentity.edge.plugin.RequestPluginService
import com.cloudentity.edge.plugin.verticle.RequestPluginVerticle
import com.cloudentity.edge.util.ConfigDecoder

import scala.concurrent.Future
import scala.util.{Failure, Success}

case class DefaultJwtRequestConf(jwtServiceAddress: Option[String])

class OutgoingDefaultJwtPlugin extends RequestPluginVerticle[DefaultJwtRequestConf] with RequestPluginService with ConfigDecoder {
  import Codecs._
  import io.circe.generic.auto._
  import io.circe.generic.semiauto._

  override def name = PluginName("outgoingDefaultJwt")
  override def confDecoder: Decoder[DefaultJwtRequestConf] = deriveDecoder

  var jwtConfig: DefaultJwtPluginConf = _
  var jwtServiceClients: JwtServices = JwtServices()

  override def initService(): Unit = {
    jwtConfig = decodeConfigUnsafe[DefaultJwtPluginConf]
    jwtServiceClients = jwtServiceClients.add(vertx, jwtConfig.jwtServiceAddress).get
  }

  override def apply(ctx: RequestCtx, conf: DefaultJwtRequestConf): Future[RequestCtx] = for {
    service       <- jwtServiceClients.get(conf.jwtServiceAddress.getOrElse(jwtConfig.jwtServiceAddress))
    ctxWithToken  <- buildRequestWithJwtToken(ctx, jwtConfig, service)
  } yield ctxWithToken

  override def validate(conf: DefaultJwtRequestConf): ValidateResponse = conf.jwtServiceAddress match {
    case Some(address) => jwtServiceClients.add(vertx, jwtConfig.jwtServiceAddress) match {
      case Success(clients) =>
        jwtServiceClients = clients
        ValidateOk
      case Failure(error) =>
        ValidateError(s"Failed to create $address JwtService for OutgoingDefaultJwtPlugin: ${error.getMessage}")
    }
    case None => ValidateOk
  }

  def buildRequestWithJwtToken(ctx: RequestCtx, conf: DefaultJwtPluginConf, client: JwtService): Future[RequestCtx] = for {
    emptyToken <- client.empty().toScala()
    withClaims  = emptyToken.withCtxClaims(ctx.authnCtx)
    encoded    <- client.sign(withClaims).toScala()
    authHeaderName = conf.authHeader.flatMap(_.name).getOrElse("Authorization")
    authHeaderValue = conf.authHeader.flatMap(_.pattern).getOrElse("Bearer {}").replace("{}", encoded)
  } yield ctx.modifyRequest(_.modifyHeaders(_.set(authHeaderName, authHeaderValue)))
}
