package com.cloudentity.pyron.domain.flow

import com.cloudentity.pyron.domain.http.{ApiResponse, Headers, OriginalRequest, TargetRequest}
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.circe.Json
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.core.streams.ReadStream

import scala.concurrent.{ExecutionContext, Future}

case class RequestCtx(targetRequest: TargetRequest,
                      originalRequest: OriginalRequest,
                      bodyStreamOpt: Option[ReadStream[Buffer]],
                      modifyResponse: List[ApiResponse => Future[ApiResponse]] = Nil,
                      proxyHeaders: ProxyHeaders,
                      properties: Properties = Properties(),
                      authnCtx: AuthnCtx = AuthnCtx(),
                      tracingCtx: TracingContext,
                      accessLog: AccessLogItems = AccessLogItems(),
                      aborted: Option[ApiResponse] = None,
                      failed: Option[FlowFailure] = None) {


  def modifyHeaders(f: Headers => Headers): RequestCtx =
    modifyRequest(v => v.copy(headers = f(v.headers)))

  def dropBody(): RequestCtx =
    modifyRequest(_.copy(bodyOpt = Some(Buffer.buffer())))

  def setBody(newBody: JsonObject): RequestCtx =
    modifyRequest(_.copy(bodyOpt = Some(newBody.toBuffer)))

  def modifyRequest(f: TargetRequest => TargetRequest): RequestCtx =
    this.copy(targetRequest = f(targetRequest))

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

  def isAborted: Boolean =
    aborted.isDefined

  def isFailed: Boolean =
    failed.isDefined
}
