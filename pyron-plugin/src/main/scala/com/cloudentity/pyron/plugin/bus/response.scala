package com.cloudentity.pyron.plugin.bus

import com.cloudentity.pyron.domain.flow.{PluginConf, ResponseCtx}

object response {
  case class ApplyRequest(ctx: ResponseCtx, conf: PluginConf)

  sealed trait ApplyResponse
    case class Continue(ctx: ResponseCtx) extends ApplyResponse
    case class ApplyError(ex: Throwable) extends ApplyResponse
}