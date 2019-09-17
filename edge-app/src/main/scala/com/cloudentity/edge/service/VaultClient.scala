package com.cloudentity.edge.service

import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.security.cert.{CertificateFactory, X509Certificate}

import io.circe.parser.decode
import com.cloudentity.edge.util.ConfigDecoder
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.vertx.core.{Future, Promise}
import io.vertx.core.http.{HttpClientOptions => JavaHttpClientOptions}
import io.vertx.core.http.{HttpClient, HttpClientOptions, HttpClientResponse}
import org.slf4j.LoggerFactory
import sun.security.provider.X509Factory

import scala.util.{Failure, Success, Try}
import scalaz.{-\/, \/, \/-}

sealed trait VaultClientError
case class VaultClientHttpError(ex: Throwable) extends VaultClientError
case class VaultInvalidStatusCodeError(status: Int) extends VaultClientError
case class VaultParsingError() extends VaultClientError

case class VaultServiceConf(ssl: Boolean, host: String, port: Int, path: String, timeout: Int, debug: Boolean,
                           trustAll: Boolean, publicKeyEndpoint: String)

case class VaultPublicKeyData(certificate: String, revocation_time: Long)
case class VaultPublicKeyResponse(request_id: String, lease_id: String, renewable: Boolean, data: VaultPublicKeyData)
case class VaultErrorResponse(errors: List[String])


trait VaultClient {
  /**
    * Returns X509Certificate for serial id
    *
    * @return client exception or option of X509Certificate
    *         - if response is None that means that public key was not found in Vault
    */
  @VertxEndpoint
  def getPublicKey(serial: String): Future[VaultClientError \/ Option[X509Certificate]]
}

class HttpVaultClient extends ScalaServiceVerticle with VaultClient with ConfigDecoder {
  val log = LoggerFactory.getLogger(this.getClass)

  var httpClient: HttpClient = _
  var vaultServiceConf: VaultServiceConf = _

  override def initService(): Unit = {
    import io.circe.generic.auto._

    vaultServiceConf = decodeConfigUnsafe[VaultServiceConf]("vaultService")
    httpClient = vertx.createHttpClient(httpClientOptions)
  }

  private def httpClientOptions = {
    val options = new JavaHttpClientOptions()
    options.setDefaultHost(vaultServiceConf.host)
    options.setDefaultPort(vaultServiceConf.port)
    options.setSsl(vaultServiceConf.ssl)
    options.setTrustAll(vaultServiceConf.trustAll)
    options.setConnectTimeout(vaultServiceConf.timeout)
    new HttpClientOptions(options)
  }

  def getPublicKey(serial: String): Future[VaultClientError \/ Option[X509Certificate]] = {
    val promise: Promise[VaultClientError \/ Option[X509Certificate]] = Promise.promise[VaultClientError \/ Option[X509Certificate]]()
    httpClient.get(s"${vaultServiceConf.path}${vaultServiceConf.publicKeyEndpoint}/${serial}" , (response: HttpClientResponse) => {
      response.statusCode() match {
        case 200 => handleResponse(response, promise)
        case 404 => handleNotFoundFailure(response, promise)
        case _ => handleGeneralFailure(response, promise)
      }
    })
      .exceptionHandler(error => promise.complete(-\/(VaultClientHttpError(error))))
      .end()
    promise.future()
  }

  def handleResponse(response: HttpClientResponse, promise: Promise[VaultClientError \/ Option[X509Certificate]]) : Unit = {
    response.bodyHandler(body => {
      import io.circe.generic.auto._

      decode[VaultPublicKeyResponse](body.toString(Charset.defaultCharset())) match {
        case Right(publicKeyResponse) => {
          log.debug(s"Public key response: ${publicKeyResponse}")
          toX509Certificate(publicKeyResponse.data.certificate) match {
            case Success(cert) => promise.complete(\/-(Some(cert)))
            case Failure(err)  => promise.fail(err)
          }
        }
        case Left(ex) => {
          log.error(s"Failed to decode Vault public key response: $ex", ex)
          promise.complete(-\/(VaultParsingError()))
        }
      }
    })
  }

  var UNSUPPORTED_PATH_ERROR: String = "unsupported path"

  def handleNotFoundFailure(response: HttpClientResponse, promise: Promise[\/[VaultClientError, Option[X509Certificate]]]): Unit = {
    response.bodyHandler(body => {
      import io.circe.generic.auto._

      val responseBody = body.toString(Charset.defaultCharset())
      decode[VaultErrorResponse](responseBody) match {
        case Right(errorResponse) => {
          log.debug(s"Exception response from Vault: ${errorResponse}")
          if (errorResponse.errors.size == 1 && errorResponse.errors.contains(UNSUPPORTED_PATH_ERROR)) {
            promise.complete(\/-(None))
          } else {
            handleGeneralFailure(response, promise)
          }
        }
        case Left(ex) => {
          log.error(s"Unexpected content in NotFound response: ${response}. Exception: $ex", ex)
          promise.complete(-\/(VaultParsingError()))
        }
      }
    })
  }

  def handleGeneralFailure(response: HttpClientResponse, promise: Promise[\/[VaultClientError, Option[X509Certificate]]]): Unit = {
    log.error("Received unexpected status code from Vault")
    promise.complete(-\/(VaultInvalidStatusCodeError(response.statusCode())))
  }

  private def toX509Certificate(certificate: String): Try[X509Certificate] = Try {
    val certSanitized = certificate
      .replace(X509Factory.BEGIN_CERT, "")
      .replace(X509Factory.END_CERT, "")
      .replace("\n","")
    val decoded = java.util.Base64.getDecoder.decode(certSanitized)
    CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(decoded))
      .asInstanceOf[X509Certificate]
  }

}