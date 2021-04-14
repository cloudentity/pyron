package com.cloudentity.pyron.api

import com.cloudentity.pyron.api.body.{BodyBuffer, BodyLimit, RequestBodyTooLargeException, SizeLimitedBodyStream}
import com.cloudentity.pyron.apigroup.ApiGroup
import com.cloudentity.pyron.client.TargetResponse
import com.cloudentity.pyron.domain.flow._
import com.cloudentity.pyron.domain.http._
import com.cloudentity.pyron.domain.rule._
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.MultiMap
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.streams.ReadStream
import io.vertx.ext.web.RoutingContext

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.Try

object HttpConversions {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  def toRequestCtx(defaultRequestBodyMaxSize: Option[Kilobytes],
                   ctx: RoutingContext,
                   tracingCtx: TracingContext,
                   proxyHeaders: ProxyHeaders,
                   ruleConf: RuleConf,
                   apiGroup: ApiGroup,
                   pathParams: PathParams)
                  (implicit ec: VertxExecutionContext): Future[RequestCtx] = {

    val req = ctx.request()
    val contentLengthOpt = Try(req.getHeader("Content-Length").toLong).toOption
    val requestBodyMaxSize = ruleConf.requestBodyMaxSize.orElse(defaultRequestBodyMaxSize)

    getBodyFuture(ctx, ruleConf, req, contentLengthOpt, requestBodyMaxSize)
      .map { case (bodyStreamOpt, bodyOpt) =>
        val original = toOriginalRequest(req, pathParams, bodyOpt)
        val dropBody = ruleConf.requestBody.contains(DropBody)
        val request = TargetRequest(
          method = ruleConf.rewriteMethod.map(_.value).getOrElse(original.method),
          service = TargetService(ruleConf.target, req),
          uri = chooseRelativeUri(tracingCtx, apiGroup.matchCriteria.basePathResolved, ruleConf, original),
          headers = removeHeadersAsProxy(ruleConf, original.headers),
          bodyOpt = bodyOpt
        ).modifyHeaders(hs => if (!dropBody) hs else hs.set("Content-Length", "0"))

        RequestCtx(
          request,
          bodyStreamOpt,
          original,
          Properties(RoutingCtxData.propertiesKey -> ctx, ApiGroup.propertiesKey -> apiGroup),
          tracingCtx,
          proxyHeaders,
          AuthnCtx(),
          aborted = None
        )
      }
  }

  private def getBodyFuture(ctx: RoutingContext,
                            ruleConf: RuleConf,
                            req: HttpServerRequest,
                            contentLengthOpt: Option[Long],
                            requestBodyMaxSize: Option[Kilobytes]): Future[(Option[ReadStream[Buffer]], Option[Buffer])] = {
    if (BodyLimit.isMaxSizeExceeded(contentLengthOpt.getOrElse(-1), requestBodyMaxSize)) {
      Future.failed(new RequestBodyTooLargeException())
    } else {
      ruleConf.requestBody.getOrElse(BufferBody) match {
        case BufferBody => BodyBuffer.bufferBody(req, contentLengthOpt, requestBodyMaxSize)
        case StreamBody =>
          requestBodyMaxSize match {
            case Some(maxSize) =>
              Future.successful((Some(SizeLimitedBodyStream(ctx.request(), maxSize)), None))
            case None =>
              Future.successful((Some(req), None))
          }
        case DropBody => Future.successful((None, None))
      }
    }
  }

  def toOriginalRequest(req: HttpServerRequest,
                        pathParams: PathParams,
                        bodyOpt: Option[Buffer]): OriginalRequest = {
    OriginalRequest(
      method = req.method(),
      path = UriPath(Option(req.path()).getOrElse("")),
      scheme = req.scheme(),
      host = req.host(),
      localHost = req.localAddress().host(),
      remoteHost = req.remoteAddress().host(),
      pathParams = pathParams,
      queryParams = toQueryParams(req.params()),
      headers = toHeaders(req.headers()),
      cookies = req.cookieMap().asScala.toMap.mapValues(cookie => cookie.getValue),
      bodyOpt = bodyOpt
    )
  }

  def chooseRelativeUri(ctx: TracingContext,
                        basePath: BasePath,
                        ruleConf: RuleConf,
                        original: OriginalRequest): RelativeUri = {
    ruleConf.rewritePath match {
      case Some(rewritePath) =>
        val queryParams = if (ruleConf.copyQueryOnRewrite.getOrElse(true)) original.queryParams else QueryParams.of()
        RewritableRelativeUri(rewritePath, queryParams, original.pathParams)
      case None =>
        val targetPath = original.path.value.drop(basePath.value.length +
          (if (ruleConf.dropPathPrefix) ruleConf.criteria.rewrite.pathPrefix.length else 0))
        FixedRelativeUri(UriPath(targetPath), original.queryParams, original.pathParams)
    }
  }

  def removeHeadersAsProxy(conf: RuleConf, headers: Headers): Headers = {
    val hs =
      if (conf.preserveHostHeader.getOrElse(false)) headers
      else headers.remove("Host")
    removeConnectionHeaders(hs)
  }

  def removeConnectionHeaders(headers: Headers): Headers =
    headers.getValues("Connection").getOrElse(Nil)
      .foldLeft(headers) { case (hs, header) => hs.remove(header) }
      .remove("Connection")

  def toApiResponse(targetResponse: TargetResponse): ApiResponse =
    ApiResponse(targetResponse.http.statusCode(), targetResponse.body, toHeaders(targetResponse.http.headers()))

  def toHeaders(from: MultiMap): Headers = from.names().asScala.foldLeft(Headers()) {
    case (hs, name) => hs.addValues(name, from.getAll(name).asScala.toList)
  }

  def toQueryParams(from: MultiMap): QueryParams = from.names().asScala.foldLeft(QueryParams()) {
    case (ps, name) => ps.addValues(name, from.getAll(name).asScala.toList)
  }
}
