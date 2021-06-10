package com.cloudentity.pyron.plugin.impl.transform

import com.cloudentity.pyron.domain.flow.{PathParams, RequestCtx}

object TransformPathParams extends TransformPathParams

trait TransformPathParams {
  def transformPathParams(pathParamsOps: ResolvedPathParamOps)(ctx: RequestCtx): RequestCtx = {
    val transformedPathParams = applyPathParamsTransformations(pathParamsOps)(ctx.targetRequest.uri.pathParams)
    ctx.modifyRequest(_.modifyPathParams(_ => transformedPathParams))
  }

  def applyPathParamsTransformations(pathParamOps: ResolvedPathParamOps)(pathParams: PathParams): PathParams =
    setPathParams(pathParamOps.set.getOrElse(Map()))(pathParams)

  def setPathParams(set: Map[String, Option[String]])(pathParams: PathParams): PathParams = {
    set.foldLeft(pathParams) { case (params, (paramName, valueOpt)) =>
      valueOpt match {
        case Some(value) => PathParams(params.value.updated(paramName, value))
        case None => PathParams(params.value - paramName)
      }
    }
  }
}