package com.cloudentity.pyron.domain.flow

import com.cloudentity.pyron.domain.http.{ApiResponse, Headers, OriginalRequest, TargetRequest}
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.circe.Json
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject

case class ResponseCtx(response: ApiResponse,
                       targetResponse: Option[ApiResponse],
                       targetRequest: TargetRequest,
                       originalRequest: OriginalRequest,
                       properties: Properties = Properties(),
                       authnCtx: AuthnCtx = AuthnCtx(),
                       tracingCtx: TracingContext,
                       accessLog: AccessLogItems = AccessLogItems(),
                       aborted: Option[ApiResponse] = None,
                       failed: Option[FlowFailure] = None) {

  def modifyHeaders(f: Headers => Headers): ResponseCtx =
    this.modifyResponse(v => v.copy(headers = f(v.headers)))

  def dropBody(): ResponseCtx =
    modifyResponse(_.withBody(Buffer.buffer()))

  def setBody(newBody: JsonObject): ResponseCtx =
    modifyResponse(_.withBody(newBody.toBuffer))

  def modifyResponse(f: ApiResponse => ApiResponse): ResponseCtx =
    this.copy(response = f(response))

  def withTracingCtx(ctx: TracingContext): ResponseCtx =
    this.copy(tracingCtx = ctx)

  def modifyProperties(f: Properties => Properties): ResponseCtx =
    this.copy(properties = f(properties))

  def modifyAccessLog(f: AccessLogItems => AccessLogItems): ResponseCtx =
    this.copy(accessLog = f(accessLog))

  def withAccessLog(name: String, value: Json): ResponseCtx =
    this.copy(accessLog = accessLog.updated(name, value))

  def isFailed: Boolean =
    failed.isDefined
}
