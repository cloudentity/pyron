package com.cloudentity.edge.plugin.impl.authn;

import com.cloudentity.edge.domain.flow.RequestCtx;
import com.cloudentity.tools.vertx.bus.VertxEndpoint;
import io.vertx.core.Future;
import scala.Option;

public interface AuthnProvider {
  /**
   * If TargetRequest contains all the required attribute to perform authentication
   * then it should return Future[Some[AuthnProviderResult]]. Otherwise Future[None]
   */
  @VertxEndpoint
  Future<Option<AuthnPlugin.AuthnProviderResult>> authenticate(RequestCtx req, AuthnMethodConf methodConf);

  @VertxEndpoint
  Future<String> tokenType();
}
