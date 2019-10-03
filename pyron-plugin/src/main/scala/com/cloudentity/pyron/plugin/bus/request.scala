package com.cloudentity.pyron.plugin.bus

import com.cloudentity.pyron.domain.flow.{PluginConf, RequestCtx}

object request {
  case class ApplyRequest(ctx: RequestCtx, conf: PluginConf)

  sealed trait ApplyResponse
    case class Continue(ctx: RequestCtx) extends ApplyResponse
    case class ApplyError(ex: Throwable) extends ApplyResponse
}