package com.cloudentity.edge.plugin.bus

import com.cloudentity.edge.domain.flow.{PluginConf, RequestCtx}

object request {
  case class ApplyRequest(ctx: RequestCtx, conf: PluginConf)

  sealed trait ApplyResponse
    case class Continue(ctx: RequestCtx) extends ApplyResponse
    case class ApplyError(msg: String) extends ApplyResponse
}