package com.cloudentity.pyron.plugin.bus

import com.cloudentity.pyron.domain.flow.{ApiGroupPluginConf, ResponseCtx}

object response {
  case class ApplyRequest(ctx: ResponseCtx, conf: ApiGroupPluginConf)

  sealed trait ApplyResponse
    case class Continue(ctx: ResponseCtx) extends ApplyResponse
    case class ApplyError(ex: Throwable) extends ApplyResponse
}