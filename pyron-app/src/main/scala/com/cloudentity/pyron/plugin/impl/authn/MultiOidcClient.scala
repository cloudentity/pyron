package com.cloudentity.pyron.plugin.impl.authn

import com.nimbusds.jose.jwk.JWKSet
import io.circe.Decoder
import com.cloudentity.pyron.domain.Codecs.AnyValDecoder
import MultiOidcClient._
import com.cloudentity.pyron.util.ConfigDecoder
import com.cloudentity.tools.vertx.scala.Operation
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.vertx.core.http.{HttpClient, HttpClientOptions}
import io.vertx.core.json.JsonObject
import io.vertx.core.{Future => VxFuture}
import org.slf4j.LoggerFactory
import scalaz.\/-

import scala.concurrent.Future
import com.cloudentity.pyron.domain.Codecs._

object MultiOidcClient {
  case class IdpHost(value: String) extends AnyVal
  case class JwkEndpoint(value: String) extends AnyVal
  case class BasePath(value: String) extends AnyVal

  case class IdpConf(host: IdpHost, port: Option[Int], ssl: Option[Boolean], basePath: Option[BasePath],
                     jwkEndpoint: JwkEndpoint, http: Option[JsonObject])

  case class MultiOidcClientConf(jwkReload: Long, idps: List[IdpConf])

  implicit lazy val idpHostDecoder: Decoder[IdpHost] = AnyValDecoder(IdpHost)
  implicit lazy val jwkEndpointDecoder: Decoder[JwkEndpoint] = AnyValDecoder(JwkEndpoint)
  implicit lazy val basePathDecoder: Decoder[BasePath] = AnyValDecoder(BasePath)
}

class MultiOidcClient extends ScalaServiceVerticle with OidcClient with ConfigDecoder {
  val log = LoggerFactory.getLogger(this.getClass)

  var clients: List[(HttpClient, IdpConf)] = _
  var conf: MultiOidcClientConf = _
  var keys: Option[JWKSet] = None

  override def initService(): Unit = {
    import io.circe.generic.auto._

    conf = decodeConfigUnsafe[MultiOidcClientConf]
    clients = initClients()

    registerSelfConfChangeListener { _ =>
      conf = decodeConfigUnsafe[MultiOidcClientConf]
      clients.map(x => x._1.close())
      clients = initClients()
    }

    vertx.setPeriodic(conf.jwkReload, _ => {
      log.debug("Periodical jwk keys reload")
      fetchAll().toJava().setHandler(result =>
        keys = Some(result.result())
      )
    })
  }

  def initClients(): List[(HttpClient, IdpConf)] =
    conf.idps.map(idp => (initClient(idp), idp))

  def initClient(idpConf: IdpConf): HttpClient = {
    val options = new HttpClientOptions(idpConf.http.getOrElse(new JsonObject()))

    options.setDefaultHost(idpConf.host.value)
    idpConf.port.foreach(options.setDefaultPort(_))
    idpConf.ssl.foreach(options.setSsl(_))

    vertx.createHttpClient(options)
  }

  def fetchAll(): Future[JWKSet] =
    Operation.traverseAndCollect(clients) { case (client, idpCfg) =>
      val endpointPath = s"${idpCfg.basePath.map(_.value).getOrElse("")}${idpCfg.jwkEndpoint.value}"
      OidcHttpClient.fetchKeys(client, endpointPath).toOperation
    }.map { case (errors, jwks) =>
      logErrors(errors)
      flattenSets(jwks)
    }

  def logErrors(errors: List[OidcClientError]): Unit = {
    if (errors.size > 0) {
      log.error(s"Could not load jwk from ${errors.size} IDP(s)")
    }
  }

  def flattenSets(sets: List[JWKSet]): JWKSet =
    sets.foldLeft(new JWKSet())((set, s) => {
      set.getKeys.addAll(s.getKeys)
      set
    })

  override def getPublicKeys(): VxFuture[OidcClientError \/ JWKSet] =
    keys match {
      case Some(keys) => VxFuture.succeededFuture(\/-(keys))
      case None => {
        log.debug("No jwk keys locally available, fetching")
        fetchAll().map { jwks =>
          keys = Some(jwks)
          jwks.right[OidcClientError]
        }.toJava()
      }
    }

}

