package com.cloudentity.pyron.api

import com.cloudentity.pyron.api.Responses.Errors
import com.cloudentity.pyron.api.body.{BodyBuffer, BodyLimit, RequestBodyTooLargeException, SizeLimitedBodyStream}
import com.cloudentity.pyron.apigroup.{ApiGroup, ApiGroupConf, ApiGroupsChangeListener, ApiGroupsStore}
import com.cloudentity.pyron.client.{TargetClient, TargetResponse}
import com.cloudentity.pyron.config.Conf
import com.cloudentity.pyron.config.Conf.AppConf
import com.cloudentity.pyron.domain.flow.{FlowFailure, _}
import com.cloudentity.pyron.domain.http._
import com.cloudentity.pyron.domain.rule._
import com.cloudentity.pyron.plugin.PluginFunctions
import com.cloudentity.pyron.plugin.PluginFunctions.{RequestPlugin, ResponsePlugin}
import com.cloudentity.pyron.rule.RuleMatcher.{Match, NoMatch}
import com.cloudentity.pyron.rule.{ApiGroupMatcher, Rule, RuleMatcher, RulesStore}
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.server.api.RouteHandler
import com.cloudentity.tools.vertx.server.api.tracing.RoutingWithTracingS
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.config.ConfigChange
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.ReplyException
import io.vertx.core.http.{HttpMethod, HttpServerRequest, HttpServerResponse}
import io.vertx.core.streams.ReadStream
import io.vertx.core.{MultiMap, http, Future => VxFuture}
import io.vertx.ext.web.RoutingContext
import scalaz.{-\/, \/-}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait ApiHandler {
  @VertxEndpoint
  def handle(conf: AppConf, ctx: RoutingContext): VxFuture[Unit]
}

class ApiHandlerVerticle extends ScalaServiceVerticle with ApiHandler with ApiGroupsChangeListener {

  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  import ApiRequestHandler._
  import ApiResponseHandler._
  import HttpConversions._

  var targetClient: TargetClient = _
  var apiGroups: List[ApiGroup] = List()
  var rulesStore: RulesStore = _

  case class RuleMatchInput(hostOpt: Option[String], method: HttpMethod, pathOpt: Option[String])

  override def initServiceAsyncS(): Future[Unit] = {
    registerConfChangeConsumer(onSmartClientsChanged)

    createClient(classOf[ApiGroupsStore]).getGroups().toScala()
      .map(apiGroups = _)
      .flatMap(_ => resetRulesAndSmartClients(apiGroups))
  }

  override def apiGroupsChanged(groups: List[ApiGroup], confs: List[ApiGroupConf]): Unit =
    resetRulesAndSmartClients(groups)

  private def onSmartClientsChanged(change: ConfigChange): Unit = {
    val prev = change.getPreviousConfiguration.getJsonObject(Conf.smartHttpClientsKey)
    val next = change.getNewConfiguration.getJsonObject(Conf.smartHttpClientsKey)

    if (prev != next) {
      log.info(TracingContext.dummy(), s"'${Conf.smartHttpClientsKey}' configuration changed.")
      resetRulesAndSmartClients(apiGroups)
    }
  }

  def resetRulesAndSmartClients(gs: List[ApiGroup]): Future[Unit] =
    TargetClient.resetTargetClient(vertx, getConfService, getTracing, gs.flatMap(_.rules).map(_.conf), Option(targetClient))
      .map { newTargetClient =>
        apiGroups = gs
        targetClient = newTargetClient
      }

  def handle(conf: AppConf, ctx: RoutingContext): VxFuture[Unit] = {
    val vertxRequest   = ctx.request()
    val vertxResponse  = ctx.response()
    val tracingContext = RoutingWithTracingS.getOrCreate(ctx, getTracing)
    val proxyHeaders   = getProxyHeaders(ctx)

    val requestSignature = Option(vertxRequest.host()) match {
      case Some(_) => s"${vertxRequest.method()} ${vertxRequest.uri()}"
      case None => s"${vertxRequest.method()} ${vertxRequest.absoluteURI()}"
    }
    log.trace(tracingContext, s"Received request: $requestSignature, headers=${vertxRequest.headers()}")

    def matchOpt(input: RuleMatchInput): Option[(ApiGroup, RuleWithPathParams)] =
      for {
        apiGroup             <- findMatchingApiGroup(apiGroups, input.hostOpt, input.pathOpt)
        ruleWithPathParams   <- findMatchingRule(apiGroup, input.method, input.pathOpt)
      } yield (apiGroup, ruleWithPathParams)

    type Failed = Boolean
    def reroute(targetMethod: HttpMethod, targetRelativeUri: RelativeUri, finalRequestCtx: RequestCtx): Future[(ApiResponse, Failed)] = {
      val matchData =
        RuleMatchInput(
          hostOpt = Option(vertxRequest.host()),
          method = targetMethod,
          pathOpt = Some(targetRelativeUri.path)
        )

      matchOpt(matchData) match {
        case None =>
          Future.successful(Errors.ruleNotFound.toApiResponse(), true)
        case Some((apiGroup, ruleWithPathParams)) =>
          val rule = ruleWithPathParams.rule
          val pathParams = ruleWithPathParams.params
          val queryParams = finalRequestCtx.originalRequest.queryParams
          val reroutedToPath = matchData.pathOpt.map(UriPath(_)).getOrElse(finalRequestCtx.originalRequest.path)

          val rerouted =
            finalRequestCtx.modifyRequest { targetRequest =>
              targetRequest
                .withMethod(rule.conf.rewriteMethod.map(_.value).getOrElse(targetRequest.method))
                .withUri(chooseRelativeUri(apiGroup.matchCriteria.basePathResolved, rule.conf, reroutedToPath, queryParams, pathParams))
            }

          flow(ruleWithPathParams, Future.successful(rerouted), false).map((_, false))
      }
    }

    def flow(ruleWithPathParams: RuleWithPathParams, requestCtxFut: Future[RequestCtx], rerouteLeft: Boolean): Future[ApiResponse] = {
        val rule = ruleWithPathParams.rule

          ApiRequestHandler.setRule(ctx, rule)
          setTracingOperationName(ctx, rule)

          log.debug(tracingContext, s"Found ${ruleWithPathParams.rule} for, request='$requestSignature'")

          for {
            initRequestCtx <- requestCtxFut

            finalRequestCtx <- applyRequestPlugins(
              initRequestCtx.modifyRequest(withProxyHeaders(proxyHeaders)),
              rule.requestPlugins
            )

            _                     = ApiRequestHandler.setAuthnCtx(ctx, finalRequestCtx.authnCtx)
            _                     = ApiRequestHandler.setAborted(ctx, finalRequestCtx.aborted.isDefined)
            _                     = ApiRequestHandler.setFailure(ctx, finalRequestCtx.failed)

            _                     = ApiRequestHandler.addExtraAccessLogItems(ctx, finalRequestCtx.accessLog)
            _                     = ApiRequestHandler.addProperties(ctx, finalRequestCtx.properties)

            (initResponse, fail) <- finalRequestCtx.aborted match {
                                      case None =>
                                        if (!rule.conf.reroute.getOrElse(false))
                                          callTarget(tracingContext, finalRequestCtx, rule.conf.call)
                                        else if (rerouteLeft)
                                          reroute(finalRequestCtx.targetRequest.method, finalRequestCtx.targetRequest.uri, finalRequestCtx)
                                        else
                                          Future.failed(new Exception("Too many reroutes."))
                                      case Some(response) =>
                                        Future.successful((response, false))
                                    }
            modifiedResponse     <- finalRequestCtx.modifyResponse(initResponse)
            responseCtx           = toResponseCtx(finalRequestCtx, fail, modifiedResponse)

            finalResponseCtx     <- applyResponsePlugins(responseCtx, rule.responsePlugins)

            _                     = ApiRequestHandler.addExtraAccessLogItems(ctx, finalResponseCtx.accessLog)
            _                     = ApiRequestHandler.addProperties(ctx, finalResponseCtx.properties)
          } yield finalResponseCtx.response
      }

    val matchData = RuleMatchInput(Option(vertxRequest.host()), vertxRequest.method(), Option(vertxRequest.path()))

    val program: Future[ApiResponse] =
      matchOpt(matchData) match {
        case None =>
          Future.successful(Errors.ruleNotFound.toApiResponse())
        case Some((apiGroup, ruleWithPathParams)) =>
          val initRequestCtx =
            toRequestCtx(
              conf.defaultRequestBodyMaxSize,
              ctx,
              tracingContext,
              proxyHeaders,
              ruleWithPathParams.rule.conf,
              apiGroup,
              ruleWithPathParams.params
            )

          flow(ruleWithPathParams, initRequestCtx, true)
      }

    program.onComplete { result =>
      if (conf.traceIdHeaderEnabled.contains(true)) {
        vertxResponse.putHeader("Trace-Id", tracingContext.getTraceId)
      }
      try {
        result match {
          case Success(apiResponse) =>
            handleApiResponse(tracingContext, vertxResponse, apiResponse)
          case Failure(ex) =>
            log.error(tracingContext, s"Unexpected error, request='$requestSignature'", ex)
            endWithException(tracingContext, vertxResponse, ex)
        }
      } catch { case ex: Throwable =>
        log.error(tracingContext, s"Unexpected error, request='$requestSignature'", ex)
        endWithException(tracingContext, vertxResponse, ex)
      }
    }

    VxFuture.succeededFuture(())
  }

  def applyRequestPlugins(requestCtx: RequestCtx, plugins: List[RequestPlugin]): Future[RequestCtx] =
    PluginFunctions.applyRequestPlugins(requestCtx, plugins) { ex =>
      log.error(requestCtx.tracingCtx, s"Could not apply request plugin", ex)
      exceptionToApiResponse(ex)
    }

  def applyResponsePlugins(responseCtx: ResponseCtx, plugins: List[ResponsePlugin]): Future[ResponseCtx] =
    PluginFunctions.applyResponsePlugins(responseCtx, plugins) { ex =>
      log.error(responseCtx.tracingCtx, s"Could not apply response plugin", ex)
      exceptionToApiResponse(ex)
    }

  def callTarget(tracing: TracingContext, ctx: RequestCtx, callOpts: Option[CallOpts]): Future[(ApiResponse, Boolean)] =
    targetClient.call(tracing, ctx.targetRequest, ctx.bodyStreamOpt, callOpts).map {
      case \/-(response) => (toApiResponse(response), false)
      case -\/(ex)       => (mapTargetClientException(tracing, ex), true)
    }

  def setTracingOperationName(ctx: RoutingContext, rule: Rule): Unit = {
    val name = s"${rule.conf.criteria.method} ${rule.conf.criteria.rewrite.matchPattern}"
    ctx.put(RouteHandler.urlPathKey, rule.conf.criteria.rewrite.matchPattern)
    RoutingWithTracingS.getOrCreate(ctx, getTracing).setOperationName(name)
  }

  def toResponseCtx(requestCtx: RequestCtx, targetCallFailed: Boolean, response: ApiResponse): ResponseCtx = {
    val targetResponse = if (requestCtx.isAborted || targetCallFailed) None else Some(response)
    val failed         = if (targetCallFailed) Some(CallFailure) else requestCtx.failed

    ResponseCtx(
      response,
      targetResponse,
      requestCtx.targetRequest,
      requestCtx.originalRequest,
      requestCtx.properties,
      requestCtx.authnCtx,
      requestCtx.tracingCtx,
      requestCtx.accessLog,
      requestCtx.aborted,
      failed
    )
  }

  def getProxyHeaders(ctx: RoutingContext): ProxyHeaders =
    ProxyHeadersHandler.getProxyHeaders(ctx).getOrElse(ProxyHeaders(Map(), ""))
}

object ApiHandler {
  sealed trait ApiError
    case object NoRuleError extends ApiError
    case class RequestPluginError(err: Throwable) extends ApiError
    case class ResponsePluginError(err: Throwable) extends ApiError

  case class FlowState(authnCtx: Option[AuthnCtx], rule: Option[Rule], aborted: Option[Boolean], failure: Option[FlowFailure], extraAccessLogs: AccessLogItems, properties: Properties)
}

object ApiRequestHandler {
  def setAuthnCtx(ctx: RoutingContext, authnCtx: AuthnCtx): Unit =
    RoutingCtxData.updateFlowState(ctx, _.copy(authnCtx = Some(authnCtx)))

  def setRule(ctx: RoutingContext, rule: Rule): Unit =
    RoutingCtxData.updateFlowState(ctx, _.copy(rule = Some(rule)))

  def setAborted(ctx: RoutingContext, aborted: Boolean): Unit =
    RoutingCtxData.updateFlowState(ctx, _.copy(aborted = Some(aborted)))

  def setFailure(ctx: RoutingContext, failure: Option[FlowFailure]): Unit =
    RoutingCtxData.updateFlowState(ctx, _.copy(failure = failure))

  def addExtraAccessLogItems(ctx: RoutingContext, items: AccessLogItems): Unit =
    RoutingCtxData.updateFlowState(ctx, state => state.copy(extraAccessLogs = state.extraAccessLogs.merge(items)))

  def addProperties(ctx: RoutingContext, props: Properties): Unit =
    RoutingCtxData.updateFlowState(ctx, state => state.copy(properties = Properties(state.properties.toMap ++ props.toMap)))

  case class RuleWithPathParams(rule: Rule, params: PathParams)

  def findMatchingApiGroup(apiGroups: List[ApiGroup], hostOpt: Option[String], pathOpt: Option[String]): Option[ApiGroup] =
    apiGroups.find { group =>
      ApiGroupMatcher.makeMatch(hostOpt, pathOpt.getOrElse(""), group.matchCriteria)
    }


  def findMatchingRule(apiGroup: ApiGroup, method: HttpMethod, pathOpt: Option[String]): Option[RuleWithPathParams] = {
    @tailrec
    def loop(basePath: BasePath, rules: List[Rule]): Option[RuleWithPathParams] =
      rules match {
        case rule :: tail =>
          val criteria = rule.conf.criteria
          val path = pathOpt.getOrElse("")
          RuleMatcher.makeMatch(method, path, basePath, criteria) match {
            case Match(rewrite) => Some(RuleWithPathParams(rule, rewrite.pathParams))
            case NoMatch => loop(basePath, tail)
          }
        case Nil => None
      }

    loop(apiGroup.matchCriteria.basePathResolved, apiGroup.rules)
  }

  def withProxyHeaders(proxyHeaders: ProxyHeaders)(req: TargetRequest): TargetRequest =
    req.withHeaderValues(proxyHeaders.headers)
}

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
    copyHeadersWithoutContentLength(apiResponse, vertxResponse)

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

  def copyHeadersWithoutContentLength(from: ApiResponse, to: HttpServerResponse): Unit = {
    for {
      (name, values) <- from.headers.toMap
      value          <- values
    } to.headers().add(name, value)
    dropContentLengthHeader(to)
  }

  private def isChunked(resp: ApiResponse): Boolean =
    resp.headers.getValues("Transfer-Encoding") match {
      case Some(values) => values.exists(_.contains("chunked"))
      case None         => false
    }

  /**
    * Plugins could have changed original body, without adjusting Content-Length.
    * We drop Content-Length and let Vertx http server set it.
    */
  private def dropContentLengthHeader(response: HttpServerResponse) =
    response.headers().remove("Content-Length")

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

object HttpConversions {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  def toRequestCtx(
                    defaultRequestBodyMaxSize: Option[Kilobytes],
                    ctx: RoutingContext,
                    tracingCtx: TracingContext,
                    proxyHeaders: ProxyHeaders,
                    ruleConf: RuleConf,
                    apiGroup: ApiGroup,
                    pathParams: PathParams
                  )(implicit ec: VertxExecutionContext): Future[RequestCtx] = {

    val req = ctx.request()
    val contentLengthOpt: Option[Long] = Try[Long](java.lang.Long.valueOf(req.getHeader("Content-Length"))).toOption
    val requestBodyMaxSize = ruleConf.requestBodyMaxSize.orElse(defaultRequestBodyMaxSize)

    val bodyFut: Future[(Option[ReadStream[Buffer]], Option[Buffer])] =
      if (BodyLimit.isMaxSizeExceeded(contentLengthOpt.getOrElse(-1), requestBodyMaxSize)) {
        Future.failed(new RequestBodyTooLargeException())
      } else {
        ruleConf.requestBody.getOrElse(BufferBody) match {
          case BufferBody => BodyBuffer.bufferBody(req, contentLengthOpt, requestBodyMaxSize)
          case StreamBody   =>
            requestBodyMaxSize match {
              case Some(maxSize) =>
                Future.successful((Some(SizeLimitedBodyStream(ctx.request(), maxSize)), None))
              case None =>
                Future.successful((Some(req), None))
            }

          case DropBody   => Future.successful((None, None))
        }
      }

    bodyFut.map { case (bodyStreamOpt, bodyOpt) =>
      val original = toOriginalRequest(req, pathParams, bodyOpt)
      val targetRequest =
        TargetRequest(
          method  = ruleConf.rewriteMethod.map(_.value).getOrElse(original.method),
          service = TargetService(ruleConf.target, req),
          uri     = chooseRelativeUri(apiGroup.matchCriteria.basePathResolved, ruleConf, original.path, original.queryParams, pathParams),
          headers = removeHeadersAsProxy(ruleConf, original.headers),
          bodyOpt = bodyOpt
        )

      val targetRequestWithDroppedBody =
        if (ruleConf.requestBody.contains(DropBody))
          targetRequest.modifyHeaders(_.set("Content-Length", "0"))
        else targetRequest

      RequestCtx(
        targetRequest = targetRequestWithDroppedBody,
        originalRequest = original,
        bodyStreamOpt = bodyStreamOpt,
        proxyHeaders = proxyHeaders,
        properties = Properties(RoutingCtxData.propertiesKey -> ctx, ApiGroup.propertiesKey -> apiGroup),
        authnCtx = AuthnCtx(),
        tracingCtx = tracingCtx,
        aborted = None
      )
    }
  }

  def toOriginalRequest(req: HttpServerRequest, pathParams: PathParams, bodyOpt: Option[Buffer]): OriginalRequest = {
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
      cookies = req.cookieMap().asScala.toMap.mapValues(Cookie(_)),
      bodyOpt = bodyOpt
    )
  }

  def chooseRelativeUri(basePath: BasePath, ruleConf: RuleConf, path: UriPath, queryParams: QueryParams, pathParams: PathParams): RelativeUri =
    ruleConf.rewritePath match {
      case Some(rewritePath) =>
        if (ruleConf.copyQueryOnRewrite.getOrElse(true))
          RewritableRelativeUri(rewritePath, queryParams, pathParams)
        else RewritableRelativeUri(rewritePath, QueryParams.of(), pathParams)
      case None =>
        val relativeOriginalPath =
          path.value.drop(basePath.value.length)
        val targetPath =
          if (ruleConf.dropPathPrefix) relativeOriginalPath.drop(ruleConf.criteria.rewrite.pathPrefix.length)
          else relativeOriginalPath
        FixedRelativeUri(UriPath(targetPath), queryParams, pathParams)
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

  def toHeaders(from: MultiMap): Headers =
    from.names().asScala.foldLeft(Headers()) {
      case (hs, name) => hs.addValues(name, from.getAll(name).asScala.toList)
    }

  def toQueryParams(from: MultiMap): QueryParams =
    from.names().asScala.foldLeft(QueryParams()) {
      case (ps, name) => ps.addValues(name, from.getAll(name).asScala.toList)
    }
}

