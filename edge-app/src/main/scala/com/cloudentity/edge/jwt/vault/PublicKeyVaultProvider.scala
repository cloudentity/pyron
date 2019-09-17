package com.cloudentity.edge.jwt.vault

import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap

import com.cloudentity.edge.jwt.PublicKeyProvider
import com.cloudentity.edge.service.VaultClient
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.vertx.core.Future
import org.slf4j.LoggerFactory

import scala.collection._
import scala.collection.JavaConverters._
import scalaz.{-\/, \/-}

case class PublicKeyVaultProviderConf(reloadMillis: Long = 5000)

class PublicKeyVaultProvider extends ScalaServiceVerticle with PublicKeyProvider {

  val log = LoggerFactory.getLogger(this.getClass)
  var vaultClient: VaultClient = _
  var vaultProviderConfig: PublicKeyVaultProviderConf = PublicKeyVaultProviderConf()

  val cache: concurrent.Map[String, Option[X509Certificate]] = new ConcurrentHashMap[String, Option[X509Certificate]]().asScala

  override def initService(): Unit = {
    if (Option(getConfig()).isDefined && Option(getConfig().getLong("reloadMillis")).isDefined) {
      vaultProviderConfig = PublicKeyVaultProviderConf(getConfig().getLong("reloadMillis"))
    }
    vaultClient = createClient(classOf[VaultClient])

    vertx.setPeriodic(vaultProviderConfig.reloadMillis, _ => {
      log.debug("Periodical reload of not found public keys")
      cache.filter(_._2.isEmpty).keys.foreach(key => getPublicKey(key, true).setHandler(result =>
        if (result.succeeded()) {
          log.debug(s"Reloaded public key of kid ${key}, Found: ${result.result().isDefined}")
          cache.put(key, result.result())
        } else {
          log.warn(s"Failed to reload public key of kid ${key}. Reason: ${result.cause().getMessage}")
        }
      ))
    })
  }

  override def getPublicKey(kid: String): Future[Option[X509Certificate]] = {
    getPublicKey(kid, false)
  }

  private def getPublicKey(kid: String, skipCache: Boolean): Future[Option[X509Certificate]] = {
    log.debug(s"Searching for public key of kid ${kid}")
    if (!skipCache && cache.get(kid).isDefined) {
      log.debug(s"Found public key in cache for ${kid}")
      Future.succeededFuture(cache.get(kid).get)
    } else {
      log.debug(s"Not found public key in cache for ${kid} or cache ignored (${skipCache}). Fetching from Vault")
      vaultClient.getPublicKey(kid).toScala()
        .flatMap {
          case \/-(certOpt) => {
            log.debug(s"Got response from Vault for kid ${kid}. Found: ${certOpt.isDefined}")
            cache.put(kid, certOpt)
            scala.concurrent.Future.successful(certOpt)
          }
          case -\/(ex) =>
            log.warn(s"Failed to get public cert for kid ${kid}", ex)
            scala.concurrent.Future.failed(new Exception(s"Failed to get public cert: ${ex}"))
        }
    }.toJava()
  }

}
