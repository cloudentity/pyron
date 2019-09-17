package com.cloudentity.edge.plugin.impl.authn.entities

import com.cloudentity.edge.domain.authn.CloudentityAuthnCtx
import com.cloudentity.edge.domain.flow.AuthnCtx
import com.cloudentity.edge.service.UserServiceClient
import com.cloudentity.tools.vertx.tracing.LoggingWithTracing

class CustomerIdFromUserEntityProvider extends UserEntityProvider {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  override def buildCtx(user: UserServiceClient.User): AuthnCtx = {
    CloudentityAuthnCtx(
      customerId = user.json.asObject
        .flatMap(_("customer"))
        .flatMap(_.asString)
    ).toCtx
  }
}