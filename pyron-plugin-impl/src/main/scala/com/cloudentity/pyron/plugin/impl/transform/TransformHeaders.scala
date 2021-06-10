package com.cloudentity.pyron.plugin.impl.transform

import com.cloudentity.pyron.domain.flow.{RequestCtx, ResponseCtx}
import com.cloudentity.pyron.domain.http.Headers

object TransformHeaders {

  def transformReqHeaders(headerOps: ResolvedHeaderOps)(ctx: RequestCtx): RequestCtx = {
    val transformedHeaders = applyHeadersTransformations(headerOps)(ctx.targetRequest.headers)
    ctx.modifyRequest(_.modifyHeaders(_ => transformedHeaders))
  }

  def transformResHeaders(headerOps: ResolvedHeaderOps)(ctx: ResponseCtx): ResponseCtx = {
    if (ctx.isFailed) { // do not transform a failed response
      ctx
    } else {
      val transformedHeaders = applyHeadersTransformations(headerOps)(ctx.response.headers)
      ctx.modifyResponse(_.modifyHeaders(_ => transformedHeaders))
    }
  }

  def applyHeadersTransformations(headerOps: ResolvedHeaderOps)(headers: Headers): Headers =
    setHeaders(headerOps.set.getOrElse(Map()))(headers)

  def setHeaders(set: Map[String, Option[List[String]]])(headers: Headers): Headers =
    set.foldLeft(headers) { case (hs, (headerName, valueOpt)) =>
      valueOpt match {
        case Some(value) => hs.setValues(headerName, value)
        case None => hs.remove(headerName)
      }
    }

}