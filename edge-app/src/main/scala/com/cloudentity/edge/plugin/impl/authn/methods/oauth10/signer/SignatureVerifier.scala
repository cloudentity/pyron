package com.cloudentity.edge.plugin.impl.authn.methods.oauth10.signer

import com.cloudentity.tools.vertx.bus.VertxEndpoint
import io.vertx.core.{Future => VxFuture}
import scalaz.\/

sealed trait SignatureVerifierError
case object SignatureVerifierMismatch extends SignatureVerifierError
case class SignatureVerifierFailure(ex: Throwable) extends SignatureVerifierError

trait SignatureVerifier {
  /**
    * Verifies if @param signature matches @param data signed using @param key
    *
    * @param signature Base64encoded array of bytes to verify
    * @param data - array of bytes to sign
    * @param key - array of bytes representing key used to verify signature
    * @return Future of disjunction: left if error, Unit (success) otherwise
    */
  @VertxEndpoint
  def verify(signature: Array[Byte], data: Array[Byte], key: Array[Byte]): VxFuture[SignatureVerifierError \/ Unit]
}
