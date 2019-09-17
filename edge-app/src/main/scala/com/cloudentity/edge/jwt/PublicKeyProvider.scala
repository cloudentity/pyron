package com.cloudentity.edge.jwt

import java.security.cert.X509Certificate

import com.cloudentity.tools.vertx.bus.VertxEndpoint

import io.vertx.core.Future

trait PublicKeyProvider {
  @VertxEndpoint
  def getPublicKey(kid: String): Future[Option[X509Certificate]]
}
