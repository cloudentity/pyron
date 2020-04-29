package com.cloudentity.pyron.plugin.bus

import com.cloudentity.pyron.domain.flow.{PluginConf, ApiGroupPluginConf, RequestCtx}

object request {
  case class ApplyRequest(ctx: RequestCtx, conf: ApiGroupPluginConf)

  sealed trait ApplyResponse
    case class Continue(ctx: RequestCtx) extends ApplyResponse
    case class ApplyError(ex: Throwable) extends ApplyResponse
}