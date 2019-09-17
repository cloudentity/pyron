package com.cloudentity.edge.plugin.impl.authn.methods.oauth10.loader

import com.cloudentity.tools.vertx.bus.VertxEndpoint
import io.vertx.core.{Future => VxFuture}
import scalaz.\/


sealed trait SigningKeyLoaderError
case class KeyLoaderKeyNotFound() extends SigningKeyLoaderError
case class KeyLoaderFailure(ex: Throwable) extends SigningKeyLoaderError

trait SigningKeyLoader {

  /**
    * Load signing key identified by @param key from various sources (file, vault, consul, etc).
    *
    * @param key - id of key
    * @return Future of disjunction: left if error, Array of bytes representing key otherwise
    */
  @VertxEndpoint
  def load(key: String): VxFuture[SigningKeyLoaderError \/ Array[Byte]]
}