package com.cloudentity.pyron.domain.flow

import com.cloudentity.pyron.domain.http.{ApiResponse, OriginalRequest, TargetRequest}
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.circe.Json

case class ResponseCtx(targetResponse: Option[ApiResponse],
                       response: ApiResponse,
                       request: TargetRequest,
                       originalRequest: OriginalRequest,
                       tracingCtx: TracingContext,
                       properties: Properties = Properties(),
                       authnCtx: AuthnCtx = AuthnCtx(),
                       accessLog: AccessLogItems = AccessLogItems(),
                       requestAborted: Boolean,
                       failed: Option[FlowFailure] = None) {

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

  def isFailed(): Boolean =
    failed.isDefined
}
