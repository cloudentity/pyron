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

  def toRequestCtx(ctx: RoutingContext,
                   tracingCtx: TracingContext,
                   apiGroup: ApiGroup,
                   ruleConf: RuleConf,
                   pathParams: PathParams,
                   queryParams: QueryParams,
                   proxyHeaders: ProxyHeaders,
                   defaultRequestBodyMaxSize: Option[Kilobytes])
                  (implicit ec: VertxExecutionContext): Future[RequestCtx] = {

    val req = ctx.request()

    getBodyFuture(req, ruleConf, defaultRequestBodyMaxSize)
      .map { case (bodyStreamOpt, bodyOpt) =>
        val originalRequest = toOriginalRequest(req, pathParams, queryParams, bodyOpt)
        val dropBody = ruleConf.requestBody.contains(DropBody)
        val properties = Properties(RoutingCtxData.propertiesKey -> ctx, ApiGroup.propertiesKey -> apiGroup)
        val targetRequest = TargetRequest(
          method = ruleConf.rewriteMethod.map(_.value).getOrElse(originalRequest.method),
          service = TargetService(ruleConf.target, req),
          uri = chooseRelativeUri(apiGroup.matchCriteria.basePathResolved, ruleConf, originalRequest),
          headers = removeHeadersAsProxy(ruleConf, originalRequest.headers),
          bodyOpt = bodyOpt
        ).modifyHeaders(hs => if (!dropBody) hs else hs.set("Content-Length", "0"))

        RequestCtx(
          targetRequest = targetRequest,
          originalRequest = originalRequest,
          bodyStreamOpt = bodyStreamOpt,
          proxyHeaders = proxyHeaders,
          properties = properties,
          authnCtx = AuthnCtx(),
          tracingCtx = tracingCtx,
          aborted = None
        )

      }
  }

  private def getBodyFuture(req: HttpServerRequest,
                            ruleConf: RuleConf,
                            defaultRequestBodyMaxSize: Option[Kilobytes]
                           ): Future[(Option[ReadStream[Buffer]], Option[Buffer])] = {

    val contentLengthOpt = Try(req.getHeader("Content-Length").toLong).toOption
    val requestBodyMaxSize = ruleConf.requestBodyMaxSize.orElse(defaultRequestBodyMaxSize)

    if (BodyLimit.isMaxSizeExceeded(contentLengthOpt.getOrElse(-1), requestBodyMaxSize)) {
      Future.failed(new RequestBodyTooLargeException())
    } else ruleConf.requestBody.getOrElse(BufferBody) match {
      case BufferBody => BodyBuffer.bufferBody(req, contentLengthOpt, requestBodyMaxSize)
      case StreamBody => requestBodyMaxSize
        .map(maxSize => Future.successful((Some(SizeLimitedBodyStream(req, maxSize)), None)))
        .getOrElse(Future.successful((Some(req), None)))
      case DropBody => Future.successful((None, None))
    }
  }

  def toOriginalRequest(req: HttpServerRequest,
                        pathParams: PathParams,
                        queryParams: QueryParams,
                        bodyOpt: Option[Buffer]): OriginalRequest = {
    OriginalRequest(
      method = req.method(),
      path = UriPath(Option(req.path()).getOrElse("")),
      scheme = req.scheme(),
      host = req.host(),
      localHost = req.localAddress().host(),
      remoteHost = req.remoteAddress().host(),
      pathParams = pathParams,
      queryParams = queryParams,
      headers = toHeaders(req.headers()),
      cookies = req.cookieMap().asScala.toMap.mapValues(Cookie(_)),
      bodyOpt = bodyOpt
    )
  }

  def chooseRelativeUri(basePath: BasePath,
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
