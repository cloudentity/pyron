package com.cloudentity.edge.plugin.impl.jwt

import com.cloudentity.edge.jwt.{JwtService, JwtServiceFactory}
import io.vertx.core.Vertx

import scala.concurrent.Future
import scala.util.{Success, Try}

case class AuthHeader(name: Option[String], pattern: Option[String])
case class DefaultJwtPluginConf(jwtServiceAddress: String, authHeader: Option[AuthHeader])

case class JwtServices(clients: Map[String, JwtService] = Map()) {
  def add(vertx: Vertx, address: String): Try[JwtServices] =
    JwtServices.of(vertx, address).map(nc => JwtServices(clients + (address -> nc)))

  def get(address: String): Future[JwtService] = {
    clients.get(address) match {
      case Some(service) => Future.successful(service)
      case None => Future.failed(new RuntimeException(s"JwtService $address not found"))
    }
  }
}

object JwtServices {
  def of(vertx: Vertx, address: String): Try[JwtService] = Try {
    JwtServiceFactory.createClient(vertx, address)
  }

  def of(vertx: Vertx, base: JwtServices, as: List[String]): Try[JwtServices] =
    as.foldRight[Try[JwtServices]](Success(base))((a, tz) => tz.flatMap(z => z.add(vertx, a)))
}