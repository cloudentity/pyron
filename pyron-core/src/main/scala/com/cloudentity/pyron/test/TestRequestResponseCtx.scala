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
    cookies = Map.empty,
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
    request = emptyRequest,
    bodyStreamOpt = None,
    original = emptyOriginal,
    properties = Properties(),
    tracingCtx = TracingContext.dummy(),
    proxyHeaders = ProxyHeaders(headers = Map(), trueClientIp = ""),
    authnCtx = AuthnCtx(),
    accessLog = AccessLogItems()
  )

  val emptyResponse: ApiResponse = ApiResponse(200, Buffer.buffer(), Headers())
  val emptyResponseCtx: ResponseCtx = ResponseCtx(
    targetResponse = Some(emptyResponse),
    response = emptyResponse,
    request = emptyRequest,
    originalRequest = emptyOriginal,
    tracingCtx = TracingContext.dummy(),
    properties = Properties(),
    authnCtx = AuthnCtx(),
    accessLog = AccessLogItems(),
    requestAborted = false
  )
}
