package com.cloudentity.edge.plugin.impl.authn.methods.oauth10.nonce

import com.cloudentity.edge.plugin.impl.authn.methods.oauth10.OAuth10Request
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import io.vertx.core.{Future => VxFuture}
import scalaz.\/

sealed trait NonceValidatorError
case object NonceNotUnique extends NonceValidatorError

trait NonceValidator {

  /**
    * Validates if request has not been used before
    *
    * @param params - oauth params
    * @return Future of disjunction: left if error, Unit (success) otherwise
    */
  @VertxEndpoint
  def validate(params: OAuth10Request): VxFuture[NonceValidatorError \/ Unit]
}
