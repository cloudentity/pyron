package com.cloudentity.pyron.domain.flow

import com.cloudentity.pyron.domain.http.{ApiResponse, OriginalRequest, TargetRequest}
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.circe.Json
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream

import scala.concurrent.{ExecutionContext, Future}

case class RequestCtx(request: TargetRequest,
                      bodyStreamOpt: Option[ReadStream[Buffer]],
                      original: OriginalRequest,
                      properties: Properties = Properties(),
                      tracingCtx: TracingContext,
                      proxyHeaders: ProxyHeaders,
                      authnCtx: AuthnCtx = AuthnCtx(),
                      accessLog: AccessLogItems = AccessLogItems(),
                      modifyResponse: List[ApiResponse => Future[ApiResponse]] = Nil,
                      aborted: Option[ApiResponse] = None,
                      failed: Option[FlowFailure] = None) {

  def modifyRequest(f: TargetRequest => TargetRequest): RequestCtx =
    this.copy(request = f(request))

  def modifyProperties(f: Properties => Properties): RequestCtx =
    this.copy(properties = f(properties))

  def modifyAuthnCtx(f: AuthnCtx => AuthnCtx): RequestCtx =
    this.copy(authnCtx = f(authnCtx))

  def withAuthnCtx(name: String, value: Json): RequestCtx =
    this.copy(authnCtx = authnCtx.updated(name, value))

  def modifyAccessLog(f: AccessLogItems => AccessLogItems): RequestCtx =
    this.copy(accessLog = f(accessLog))

  def withAccessLog(name: String, value: Json): RequestCtx =
    this.copy(accessLog = accessLog.updated(name, value))

  def withTracingCtx(ctx: TracingContext): RequestCtx =
    this.copy(tracingCtx = ctx)

  def modifyResponse(response: ApiResponse)(implicit ec: ExecutionContext): Future[ApiResponse] =
    modifyResponse.foldLeft(Future.successful(response)) { case (fut, mod) => fut.flatMap(mod) }

  def withModifyResponse(f: ApiResponse => ApiResponse): RequestCtx =
    withModifyResponseAsync(f.andThen(Future.successful))

  def withModifyResponseAsync(f: ApiResponse => Future[ApiResponse]): RequestCtx =
    this.copy(modifyResponse = f :: modifyResponse)

  def abort(response: ApiResponse): RequestCtx =
    this.copy(aborted = Some(response))

  def isAborted(): Boolean =
    aborted.isDefined

  def isFailed(): Boolean =
    failed.isDefined
}
