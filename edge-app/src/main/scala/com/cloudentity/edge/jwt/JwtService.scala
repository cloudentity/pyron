package com.cloudentity.edge.jwt

import java.util.Optional

import com.cloudentity.tools.vertx.bus.{ServiceClientFactory, VertxEndpoint}
import io.vertx.core.{Future, Vertx}

trait JwtService {
  /**
    * Creates a new token with initial structure (iss, iat, exp, etc)
    */
  @VertxEndpoint
  def empty(): Future[JwtToken]

  /**
    * Encodes the token
    */
  @VertxEndpoint
  def sign(token: JwtToken): Future[String]

  /**
    * Decodes the encoded token
    */
  @VertxEndpoint
  def parse(encodedToken: String): Future[JwtToken]

  /**
    * Creates and encodes empty token
    */
  @VertxEndpoint
  def emptySigned: Future[(JwtToken, String)]
}

object JwtServiceFactory {
  def createClient(vertx: Vertx, address: String): JwtService =
    ServiceClientFactory.make(vertx.eventBus(), classOf[JwtService], Optional.of(address))
}
