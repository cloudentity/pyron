package com.cloudentity.edge.plugin.impl.authn.methods

import com.cloudentity.edge.domain.authn.CloudentityAuthnCtx
import com.cloudentity.edge.domain.flow.RequestCtx
import com.cloudentity.edge.plugin.impl.authn.AuthnPlugin.AuthnSuccess
import com.cloudentity.edge.plugin.impl.authn.{AuthnMethodConf, AuthnPlugin, AuthnProvider}
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.vertx.core.Future

class AnonymousAuthnProvider extends ScalaServiceVerticle with AuthnProvider {
  override def vertxServiceAddressPrefixS: Option[String] = Option(verticleId())

  override def authenticate(ctx: RequestCtx, methodConf: AuthnMethodConf): Future[Option[AuthnPlugin.AuthnProviderResult]] =
    Future.succeededFuture(Option(AuthnSuccess(CloudentityAuthnCtx().toCtx)))

  override def tokenType(): Future[String] = Future.succeededFuture("anonymous")
}
