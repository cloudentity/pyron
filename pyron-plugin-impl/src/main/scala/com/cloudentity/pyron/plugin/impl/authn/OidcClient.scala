package com.cloudentity.pyron.plugin.impl.authn

import java.nio.charset.Charset

import com.cloudentity.pyron.util.ConfigDecoder
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.nimbusds.jose.jwk.JWKSet
import io.vertx.core.http.{HttpClient, HttpClientOptions, HttpClientResponse}
import io.vertx.core.{Future, Promise}
import org.slf4j.LoggerFactory
import scalaz.{-\/, \/, \/-}

import scala.util.Try

sealed trait OidcClientError
case class JWKSetParsingError(ex: Throwable) extends OidcClientError
case class OidClientHttpError(ex: Throwable) extends OidcClientError
case class InvalidStatusCodeError() extends OidcClientError
case class JWKSetNotAvailable() extends OidcClientError

case class OidcServiceConf(ssl: Boolean, host: String, port: Int, path: String, timeout: Int, debug: Boolean,
                           trustAll: Boolean, jwkEndpoint: String, jwkReload: Long)

trait OidcClient {
  /**
    * Returns cached JWKSet (set of public rsa keys) from local cache or makes a call to http endpoint
    * Keys are reloaded periodically every jwkReload miliseconds
    *
    * @return client exception or JWKSet
    */
  @VertxEndpoint
  def getPublicKeys(): Future[OidcClientError \/ JWKSet]
}

@Deprecated
class HttpOidcClient extends ScalaServiceVerticle with OidcClient with ConfigDecoder {
  val log = LoggerFactory.getLogger(this.getClass)

  var httpClient: HttpClient = _
  var keys: Option[JWKSet] = None
  var oidcServiceConf: OidcServiceConf = _

  override def initService(): Unit = {
    import io.circe.generic.auto._

    oidcServiceConf = decodeConfigUnsafe[OidcServiceConf]("oidcService")

    httpClient = vertx.createHttpClient(httpClientOptions)
    vertx.setPeriodic(oidcServiceConf.jwkReload, _ => {
      log.debug("Periodical jwk keys reload")
      fetchKeys().setHandler(result =>
        if (result.succeeded()) {
          result.result() match {
            case \/-(set) =>
              log.debug(s"Periodical jwk keys update: ${set}")
              keys = Some(set)
            case -\/(ex) => log.warn(s"Failed to get keys: ${ex}")
          }
        } else {
          log.error(s"Execution failed: ${result.cause()}")
        }
      )
    })
  }

  private def httpClientOptions = {
    val options = new HttpClientOptions()
    options.setDefaultHost(oidcServiceConf.host)
    options.setDefaultPort(oidcServiceConf.port)
    options.setSsl(oidcServiceConf.ssl)
    options.setTrustAll(oidcServiceConf.trustAll)
    new HttpClientOptions(options)
  }

  override def getPublicKeys(): Future[OidcClientError \/ JWKSet] = {
    keys match {
      case Some(keys) => Future.succeededFuture(\/-(keys))
      case None => {
        log.debug("No jwk keys locally available, fetching from remote")
        fetchAndStoreKeys()
      }
    }
  }

  private def fetchAndStoreKeys(): Future[OidcClientError \/ JWKSet] = {
    fetchKeys().compose(result => {
      result.foreach(v => keys = Some(v))
      Future.succeededFuture(result)
    })
  }

  private def fetchKeys(): Future[OidcClientError \/ JWKSet] = {
    OidcHttpClient.fetchKeys(httpClient, s"${oidcServiceConf.path}${oidcServiceConf.jwkEndpoint}")
  }

}

object OidcHttpClient {
  val log = LoggerFactory.getLogger(this.getClass)

  def fetchKeys(client: HttpClient, endpointPath: String): Future[OidcClientError \/ JWKSet] = {
    val promise = Promise.promise[OidcClientError \/ JWKSet]

    client.get(endpointPath, (response: HttpClientResponse) => {
      if (response.statusCode() == 200) {
        response.bodyHandler(body => {
          Try(JWKSet.parse(body.toString(Charset.defaultCharset()))).toEither match {
            case Right(jwkSet) => {
              log.debug(s"jwk set parsed: ${jwkSet}")
              promise.complete(\/-(jwkSet))
            }
            case Left(ex) => {
              log.error(s"Failed to parse jwk: ${ex}, source ${response.request().absoluteURI()}")
              promise.complete(-\/(JWKSetParsingError(ex)))
            }
          }
        })
        ()
      } else {
        log.error(s"Received ${response.statusCode()} status code when calling: ${response.request().absoluteURI()}")
        promise.complete(-\/(InvalidStatusCodeError()))
      }
    }).exceptionHandler { ex =>
      log.error(s"Exception when calling '${endpointPath}' ", ex)
      promise.complete(-\/(OidClientHttpError(ex)))
    }.end()
    promise.future()
  }

}