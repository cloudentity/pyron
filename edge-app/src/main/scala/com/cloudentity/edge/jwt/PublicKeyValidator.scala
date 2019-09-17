package com.cloudentity.edge.jwt

import java.security.cert.X509Certificate

import com.cloudentity.tools.vertx.bus.VertxEndpoint
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.vertx.core.{Future, Promise}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

trait PublicKeyValidator {
  @VertxEndpoint
  def verifyCertificateOwner(jwtCommonName: String, cert: X509Certificate): Future[Boolean]
}

class InternalPublicKeyValidator extends ScalaServiceVerticle with PublicKeyValidator {

  val log = LoggerFactory.getLogger(this.getClass)

  def verifyCertificateOwner(jwtCommonName: String, certificate: X509Certificate): Future[Boolean] = {
    val promise = Promise.promise[Boolean]()
    Try {
      certificate.getSubjectDN.getName.stripPrefix("CN=")
    } match {
      case Success(certificateCommonName) => promise.complete(jwtCommonName.equals(certificateCommonName))
      case Failure(ex) =>
        log.error(s"Error extracting certificate common name", ex)
        promise.fail(s"Error extracting certificate common name: ${ex.getMessage}")
    }
    promise.future
  }

}
