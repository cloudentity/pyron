package com.cloudentity.pyron.plugin.impl.transform

import com.cloudentity.pyron.domain.flow.ResponseCtx

object TransformHttpStatus {

  def transformHttpStatus(statusOps: Option[Int])(ctx: ResponseCtx): ResponseCtx = {
    statusOps.map(s => ctx.modifyResponse(_ => ctx.response.copy(statusCode = s))).getOrElse(ctx)
  }
}
