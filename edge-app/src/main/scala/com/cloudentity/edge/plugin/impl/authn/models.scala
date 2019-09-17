package com.cloudentity.edge.plugin.impl.authn

import io.circe.Json
import com.cloudentity.edge.plugin.impl.authn.AuthnPlugin.{AuthnEntityType, AuthnMethodName}

case class FlowCtx(name: String, value: Json)
case class AuthnPluginConf(
  methods: List[AuthnMethodName],
  entities: Option[List[AuthnEntityType]],
  optionalEntities: Option[List[AuthnEntityType]],
  tokenHeader: Option[String],
  ctxKey: Option[String]
)
case class AuthnProxyPluginResponse(ctx: List[FlowCtx])

case class AuthnTargetRequest(headers: Map[String, List[String]])
case class AuthnProxyPluginRequest(request: AuthnTargetRequest, conf: AuthnPluginConf)
