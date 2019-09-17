package com.cloudentity.edge.plugin.impl.authn.methods.oauth10.loader

import java.util.Base64

import io.circe.generic.auto._
import com.cloudentity.edge.util.ConfigDecoder
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.vertx.core.{Future => VxFuture}
import org.slf4j.LoggerFactory

import scalaz.{-\/, \/-}
import scala.util.Try

case class SigningKeyLoaderConf(publicKeys: Map[String, String])

class ConfigRsaSigningKeyLoader extends ScalaServiceVerticle with SigningKeyLoader with ConfigDecoder {
  val log = LoggerFactory.getLogger(this.getClass)
  val charset = "UTF-8"
  var conf: SigningKeyLoaderConf = _

  override def verticleId(): String = "signing-key-loader-verticle"

  override def initService(): Unit = {
    conf = decodeConfigUnsafe[SigningKeyLoaderConf]
  }

  override def load(key: String): VxFuture[SigningKeyLoaderError \/ Array[Byte]] = {
    VxFuture.succeededFuture(fetchKey(key)
      .flatMap(v => base64Decode(v)))
  }

  def base64Decode(bytes: Array[Byte]): SigningKeyLoaderError \/ Array[Byte] = {
    Try(Base64.getDecoder.decode(bytes)).toEither match {
      case Right(bytes) => \/-(bytes)
      case Left(ex) => -\/(KeyLoaderFailure(ex))
    }
  }

  def fetchKey(keyId: String): SigningKeyLoaderError \/ Array[Byte] = {
    conf.publicKeys.get(keyId) match {
      case Some(key) => Try(key.getBytes(charset)).toEither match {
        case Right(bytes) => \/-(bytes)
        case Left(ex) => -\/(KeyLoaderFailure(ex))
      }
      case None => -\/(KeyLoaderKeyNotFound())
    }
  }

}
