package com.cloudentity.edge.test

import com.cloudentity.edge.domain.flow._
import com.cloudentity.edge.domain.http._
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod

trait TestRequestResponseCtx {
  private val correlationCtx = CorrelationCtx.withFlowId

  val emptyOriginal = OriginalRequest(HttpMethod.GET, UriPath("/"), QueryParams.empty, Headers(), None, PathParams.empty)
  val emptyRequest = TargetRequest(HttpMethod.GET, StaticService(TargetHost("localhost"), 100, false), RelativeUri.of("/").get, Headers(), None)
  val emptyRequestCtx = RequestCtx(emptyRequest, emptyOriginal, Properties(), TracingContext.dummy(), ProxyHeaders(Map(), ""), correlationCtx, AuthnCtx(), AccessLogItems(), Nil)

  val emptyResponse = ApiResponse(200, Buffer.buffer(), Headers(), None)
  val emptyResponseCtx = ResponseCtx(emptyResponse, emptyRequest, emptyOriginal, correlationCtx, TracingContext.dummy(), Properties(), AuthnCtx(), AccessLogItems(), false)
}
