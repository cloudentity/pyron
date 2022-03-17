package com.cloudentity.pyron.plugin.impl.transform

import com.cloudentity.pyron.domain.flow.RequestCtx
import com.cloudentity.pyron.domain.http.QueryParams
object TransformQueryParams extends TransformQueryParams

trait TransformQueryParams {
  def transformQueryParams(queryParamsOps: ResolvedQueryParamOps)(ctx: RequestCtx): RequestCtx = {
    val transformedQueryParams = applyQueryParamsTransformations(queryParamsOps)(ctx.targetRequest.uri.query)
    ctx.modifyRequest(_.modifyQueryParams(_ => transformedQueryParams))
  }

  def applyQueryParamsTransformations(queryParamOps: ResolvedQueryParamOps)(queryParams: QueryParams): QueryParams =
    setQueryParams(queryParamOps.set.getOrElse(Map()))(queryParams)

  def setQueryParams(set: Map[String, Option[List[String]]])(queryParams: QueryParams): QueryParams = {
    set.foldLeft(queryParams) { case (ps, (paramName, valueOpt)) =>
      valueOpt match {
        case Some(value) => ps.setValues(paramName, value)
        case None => ps.remove(paramName)
      }
    }
  }
}