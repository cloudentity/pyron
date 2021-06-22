package com.cloudentity.pyron.api

import com.cloudentity.pyron.api.body.RequestBodyTooLargeException
import com.cloudentity.pyron.domain.http.ApiResponse
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.eventbus.ReplyException
import io.vertx.core.http.HttpServerResponse

import scala.collection.JavaConverters._

object ApiResponseHandler {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  import Responses._

  def mapTargetClientException(tracing: TracingContext, ex: Throwable): ApiResponse =
    if (ex.getMessage.contains("timeout") && ex.getMessage.contains("exceeded")) {
      Errors.responseTimeout.toApiResponse()
    } else if (ex.isInstanceOf[RequestBodyTooLargeException]) {
      Errors.requestBodyTooLarge.toApiResponse()
    } else {
      log.error(tracing, s"Could not call target service", ex)
      Errors.targetUnreachable.toApiResponse()
    }

  def handleApiResponse(ctx: TracingContext, vertxResponse: HttpServerResponse, apiResponse: ApiResponse): Unit = {
    copyHeadersDropContentLength(apiResponse, vertxResponse)

    if (isChunked(apiResponse)) {
      vertxResponse.setChunked(true) // don't do vertxResponse.setChunked(false) - Vertx 3.5.4 throws NPE in that case
    }

    if (!vertxResponse.closed()) {
      vertxResponse
        .setStatusCode(apiResponse.statusCode)
        .end(apiResponse.body)
    } else {
      log.debug(ctx, "Response already closed. Tried to end with success {}", apiResponse)
    }
  }

  /**
   * Plugins could have changed original body, without adjusting Content-Length.
   * We drop Content-Length and let Vertx http server set it.
   */
  def copyHeadersDropContentLength(from: ApiResponse, to: HttpServerResponse): Unit = {
    val respHeaders = to.headers()
    from.headers.toMap.foreach { case (name, values) =>
      values.foreach(value => respHeaders.add(name, value))
    }
    respHeaders.remove("Content-Length")
  }

  private def isChunked(resp: ApiResponse): Boolean =
    resp.headers.getValues("Transfer-Encoding") match {
      case Some(values) => values.exists(_.contains("chunked"))
      case None => false
    }

  def endWithException(tracing: TracingContext, response: HttpServerResponse, ex: Throwable): Unit = {
    val apiResponse = exceptionToApiResponse(ex)

    if (!response.closed()) {
      for ((k, v) <- apiResponse.headers.toMap) response.putHeader(k, v.asJava)
      response.setStatusCode(apiResponse.statusCode).end(apiResponse.body)
    } else {
      log.debug(tracing, "Response already closed. Tried to end with error {}", apiResponse)
    }
  }

  def exceptionToApiResponse(ex: Throwable): ApiResponse = {
    def isEvenBusTimeout(ex: Throwable) =
      ex.isInstanceOf[ReplyException] && ex.getMessage.contains("Timed out")

    def isBodyRequestTooLarge(ex: Throwable) =
      ex.isInstanceOf[RequestBodyTooLargeException]

    if (isEvenBusTimeout(ex) || (ex.getCause != null && isEvenBusTimeout(ex.getCause))) {
      Errors.systemTimeout.toApiResponse()
    } else if (isBodyRequestTooLarge(ex) || (ex.getCause != null && isBodyRequestTooLarge(ex.getCause))) {
      Errors.requestBodyTooLarge.toApiResponse()
    } else {
      Errors.unexpected.toApiResponse()
    }
  }
}
