package com.cloudentity.edge.plugin.impl.authn

import com.cloudentity.edge.domain.flow.RequestCtx
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import io.vertx.core.Future

trait AuthnProvider {
  /**
   * If TargetRequest contains all the required attribute to perform authentication
   * then it should return Future[Some[AuthnProviderResult]]. Otherwise Future[None]
   */
  @VertxEndpoint
  def authenticate(req: RequestCtx, methodConf: AuthnMethodConf): Future[Option[AuthnPlugin.AuthnProviderResult]]

  @VertxEndpoint
  def tokenType(): Future[String]
}
