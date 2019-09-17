package com.cloudentity.edge.plugin.impl.authn.methods.oauth10.nonce

import java.util.concurrent.TimeUnit

import io.circe.generic.auto._
import io.circe.parser._
import com.cloudentity.edge.plugin.impl.authn.methods.oauth10.OAuth10Request
import com.cloudentity.edge.util.ConfigDecoder
import com.cloudentity.tools.vertx.bus.ServiceVerticle
import com.cloudentity.tools.vertx.hazelcast.{HazelcastService, HazelcastServiceClient}
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.vertx.core.logging.LoggerFactory
import io.vertx.core.{Future => VxFuture}

import scalaz.{-\/, \/, \/-}
import scala.concurrent.duration.Duration

case class SimpleNonceValidatorConf(nonceTtlInSeconds: Option[Int])

class HazelcastNonceValidator extends ServiceVerticle with NonceValidator with ConfigDecoder  {
  val log = LoggerFactory.getLogger(this.getClass)

  val cacheCollectionName = "oauth10nonceCache"
  val defaultNonceTtlInSeconds = 15

  var conf: SimpleNonceValidatorConf = _
  var cache: HazelcastServiceClient = _

  override def initService(): Unit = {
    conf = decodeConfigUnsafe[SimpleNonceValidatorConf]
    cache = HazelcastServiceClient(createClient(classOf[HazelcastService]))
  }

  override def validate(params: OAuth10Request): VxFuture[NonceValidatorError \/ Unit] = {
    val key = params.consumerKey + "." + params.timestamp.toString + "." + params.nonce
    val ttl = Duration(conf.nonceTtlInSeconds.getOrElse(defaultNonceTtlInSeconds), TimeUnit.SECONDS)

    cache.getValue[String](cacheCollectionName, key)
      .compose { resOpt: Option[String] => resOpt match {
        case Some(_) =>
          VxFuture.succeededFuture(-\/(NonceNotUnique))
        case None =>
          log.debug(TracingContext.dummy(), s"Nonce not found, store ${key} with ttl: ${ttl} to block future attempts")
          cache.putValue(cacheCollectionName, key, "ok", ttl).map(_ => \/-(()))
      }}
  }

}
