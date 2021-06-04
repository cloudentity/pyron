package com.cloudentity.pyron.test

import com.cloudentity.pyron.domain.flow._
import com.cloudentity.pyron.domain.http.{QueryParams, _}
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod

trait TestRequestResponseCtx {
  val emptyOriginal: OriginalRequest = OriginalRequest(
    method = HttpMethod.GET,
    path = UriPath("/"),
    scheme = "http",
    host = "host",
    localHost = "localHost",
    remoteHost = "remoteHost",
    pathParams = PathParams.empty,
    queryParams = QueryParams.empty,
    headers = Headers(),
    cookies = Map(),
    bodyOpt = None
  )
  val emptyRequest: TargetRequest = TargetRequest(
    method = HttpMethod.GET,
    service = StaticService(host = TargetHost("localhost"), port = 100, ssl = false),
    uri = RelativeUri.of("/").get,
    headers = Headers(),
    bodyOpt = None
  )
  val emptyRequestCtx: RequestCtx = RequestCtx(
    targetRequest = emptyRequest,
    originalRequest = emptyOriginal,
    bodyStreamOpt = None,
    proxyHeaders = ProxyHeaders(headers = Map(), trueClientIp = ""),
    properties = Properties(),
    authnCtx = AuthnCtx(),
    tracingCtx = TracingContext.dummy(),
    accessLog = AccessLogItems()
  )

  val emptyResponse: ApiResponse = ApiResponse(200, Buffer.buffer(), Headers())
  val emptyResponseCtx: ResponseCtx = ResponseCtx(
    response = emptyResponse,
    targetResponse = Some(emptyResponse),
    targetRequest = emptyRequest,
    originalRequest = emptyOriginal,
    properties = Properties(),
    authnCtx = AuthnCtx(),
    tracingCtx = TracingContext.dummy(),
    accessLog = AccessLogItems(),
    aborted = None
  )
}
