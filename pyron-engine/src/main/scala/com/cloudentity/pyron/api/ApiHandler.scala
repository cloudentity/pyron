package com.cloudentity.pyron.api

import com.cloudentity.pyron.apigroup.{ApiGroup, ApiGroupsChanged, ApiGroupsStore, ApiGroupsStoreVerticle}
import com.cloudentity.pyron.client.{TargetClient, TargetResponse}
import com.cloudentity.pyron.config.Conf
import com.cloudentity.pyron.domain.flow._
import com.cloudentity.pyron.domain.http._
import com.cloudentity.pyron.domain.rule.RuleConf
import com.cloudentity.pyron.plugin.PluginFunctions
import com.cloudentity.pyron.plugin.PluginFunctions.{RequestPlugin, ResponsePlugin}
import com.cloudentity.pyron.rule.{ApiGroupMatcher, Rule, RuleMatcher, RulesStore}
import com.cloudentity.pyron.rule.RuleMatcher.{Match, NoMatch}
import com.cloudentity.tools.vertx.bus.{VertxBus, VertxEndpoint}
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.server.api.RouteHandler
import com.cloudentity.tools.vertx.server.api.tracing.RoutingWithTracingS
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.config.ConfigChange
import io.vertx.core.buffer.Buffer
import io.vertx.core.{Future => VxFuture}
import io.vertx.core.MultiMap
import io.vertx.core.eventbus.ReplyException
import io.vertx.core.http.{HttpServerRequest, HttpServerResponse}
import io.vertx.ext.web.RoutingContext

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scalaz.{-\/, EitherT, \/, \/-}

import scala.collection.JavaConverters._

trait ApiHandler {
  @VertxEndpoint
  def handle(ctx: RoutingContext): VxFuture[Unit]
}

class ApiHandlerVerticle extends ScalaServiceVerticle with ApiHandler {

  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  import ApiHandler._
  import ApiRequestHandler._
  import ApiResponseHandler._
  import HttpConversions._
  import Responses._

  var targetClient: TargetClient = _
  var apiGroups: List[ApiGroup] = List()
  var rulesStore: RulesStore = _
  var routingCtxService: RoutingCtxService = _

  override def initServiceAsyncS(): Future[Unit] = {
    routingCtxService = createClient(classOf[RoutingCtxService])
    registerConfChangeConsumer(onSmartClientsChanged)
    VertxBus.consumePublished(vertx.eventBus(), ApiGroupsStoreVerticle.PUBLISH_API_GROUPS_ADDRESS, classOf[ApiGroupsChanged], (change: ApiGroupsChanged) => resetRulesAndSmartClients(change.groups))

    createClient(classOf[ApiGroupsStore]).getGroups().toScala()
      .map(apiGroups = _)
      .flatMap(_ => resetRulesAndSmartClients(apiGroups))
  }

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

  def handle(ctx: RoutingContext): VxFuture[Unit] = {
      val vertxRequest   = ctx.request()
      val vertxResponse  = ctx.response()
      val bodyOpt        = ctx.getBody()
      val tracingContext = RoutingWithTracingS.getOrCreate(ctx, getTracing)

      val requestSignature = Option(vertxRequest.host()) match {
        case Some(_) => s"${vertxRequest.method()} ${vertxRequest.uri()}"
        case None => s"${vertxRequest.method()} ${vertxRequest.absoluteURI()}"
      }
      log.debug(tracingContext, s"Received request: $requestSignature")
      log.trace(tracingContext, s"Received request: $requestSignature, headers=${vertxRequest.headers()}")

      val program: Future[ApiError \/ ApiResponse] = {
        for {
          apiGroup             <- findMatchingApiGroup(apiGroups, vertxRequest) |> Future.successful |> EitherT.apply
          ruleWithPathParams   <- findMatchingRule(apiGroup, vertxRequest) |> Future.successful |> EitherT.apply
          rule                  = ruleWithPathParams.rule
          _                     = ApiRequestHandler.setRule(ctx, rule)
          pathParams            = ruleWithPathParams.params
          _                     = log.debug(tracingContext, s"Found ${ruleWithPathParams.rule} for, request='$requestSignature'")
          _                     = setTracingOperationName(ctx, rule)
          proxyHeaders         <- getProxyHeaders(ctx) |> EitherT.apply
          initialRequestCtx     = toRequestCtx(ctx, tracingContext, proxyHeaders, rule.conf, apiGroup.matchCriteria.basePathResolved, pathParams, Option(bodyOpt)).modifyRequest(withProxyHeaders(proxyHeaders))

          finalRequestCtx      <- applyRequestPlugins(initialRequestCtx, rule.requestPlugins) |> EitherT.apply

          _                     = ApiRequestHandler.setAuthnCtx(ctx, finalRequestCtx.authnCtx)
          _                     = ApiRequestHandler.setAborted(ctx, finalRequestCtx.aborted.isDefined)
          _                     = ApiRequestHandler.addExtraAccessLogItems(ctx, finalRequestCtx.accessLog)

          initialResponse      <- finalRequestCtx.aborted match {
                                    case None =>
                                      callTarget(tracingContext, finalRequestCtx.request, rule.conf.call) |> EitherT[Future, ApiError, ApiResponse]
                                    case Some(response) =>
                                      \/-(response) |> Future.successful[ApiError \/ ApiResponse] |> EitherT[Future, ApiError, ApiResponse]
                                  }
          modifiedResponse     <- finalRequestCtx.modifyResponse(initialResponse).map(\/-(_)) |> EitherT[Future, ApiError, ApiResponse]
          responseCtx           = toResponseCtx(finalRequestCtx, modifiedResponse)
          finalResponseCtx     <- applyResponsePlugins(responseCtx, rule.responsePlugins) |> EitherT.apply
          _                     = ApiRequestHandler.addExtraAccessLogItems(ctx, finalResponseCtx.accessLog)
        } yield finalResponseCtx.response
      }.run

      program.onComplete { result =>
        try {
          cleanup(ctx)
          result match {
            case Success(\/-(apiResponse)) =>
              handleApiResponse(tracingContext, requestSignature, vertxResponse, apiResponse)
            case Success(-\/(apiError)) =>
              handleApiError(tracingContext, requestSignature, vertxResponse, apiError)
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

  def applyRequestPlugins(requestCtx: RequestCtx, plugins: List[RequestPlugin]): Future[ApiError \/ RequestCtx] =
    PluginFunctions.applyRequestPlugins(requestCtx, plugins)
      .map(\/-(_))
      .recover { case ex => -\/(RequestPluginError(ex)) }

  def applyResponsePlugins(responseCtx: ResponseCtx, plugins: List[ResponsePlugin]): Future[ApiError \/ ResponseCtx] =
    PluginFunctions.applyResponsePlugins(responseCtx, plugins)
      .map(\/-(_))
      .recover { case ex => -\/(ResponsePluginError(ex)) }

  def callTarget(ctx: TracingContext, targetRequest: TargetRequest, callOpts: Option[CallOpts]): Future[ApiError \/ ApiResponse] =
    targetClient.call(ctx, targetRequest, callOpts)
      .map {
        case \/-(resp) => \/-(toApiResponse(resp))
        case -\/(err)  => -\/(TargetApiError(err))
      }

  def setTracingOperationName(ctx: RoutingContext, rule: Rule): Unit = {
    val name = s"${rule.conf.criteria.method} ${rule.conf.criteria.path.originalPath}"
    ctx.put(RouteHandler.urlPathKey, rule.conf.criteria.path.originalPath)
    RoutingWithTracingS.getOrCreate(ctx, getTracing).setOperationName(name)
  }

  def toResponseCtx(requestCtx: RequestCtx, response: ApiResponse): ResponseCtx = ResponseCtx(
    response, requestCtx.request, requestCtx.original, requestCtx.correlationCtx, requestCtx.tracingCtx,
    requestCtx.properties, requestCtx.authnCtx, requestCtx.accessLog, requestCtx.aborted.isDefined
  )

  def cleanup(ctx: RoutingContext): Unit =
    routingCtxService.remove(RoutingCtxData.getFlowId(ctx))

  def getProxyHeaders(ctx: RoutingContext): Future[ApiError \/ ProxyHeaders] =
    ProxyHeadersHandler.getProxyHeaders(ctx) match {
      case Some(headers) => Future.successful(\/-(headers))
      case None          => Future.failed(new Exception("ProxyHeaders not set in RoutingContext"))
    }
}

object ApiHandler {
  sealed trait ApiError
    case object NoRuleError extends ApiError
    case class RequestPluginError(err: Throwable) extends ApiError
    case class ResponsePluginError(err: Throwable) extends ApiError
    case class TargetApiError(err: Throwable) extends ApiError

  case class FlowState(authnCtx: Option[AuthnCtx], rule: Option[Rule], aborted: Option[Boolean], extraAccessLogs: AccessLogItems)
}

object ApiRequestHandler {
  import ApiHandler._

  def setAuthnCtx(ctx: RoutingContext, authnCtx: AuthnCtx): Unit =
    RoutingCtxData.updateFlowState(ctx, _.copy(authnCtx = Some(authnCtx)))

  def setRule(ctx: RoutingContext, rule: Rule): Unit =
    RoutingCtxData.updateFlowState(ctx, _.copy(rule = Some(rule)))

  def setAborted(ctx: RoutingContext, aborted: Boolean): Unit =
    RoutingCtxData.updateFlowState(ctx, _.copy(aborted = Some(aborted)))

  def addExtraAccessLogItems(ctx: RoutingContext, items: AccessLogItems): Unit =
    RoutingCtxData.updateFlowState(ctx, state => state.copy(extraAccessLogs = state.extraAccessLogs.merge(items)))

  case class RuleWithPathParams(rule: Rule, params: PathParams)

  def findMatchingApiGroup(apiGroups: List[ApiGroup], vertxRequest: HttpServerRequest): ApiError \/ ApiGroup = {
    apiGroups.find { group =>
      ApiGroupMatcher.makeMatch(Option(vertxRequest.host()), Option(vertxRequest.path()).getOrElse(""), group.matchCriteria)
    } match {
      case Some(apiGroup) =>
        \/-(apiGroup)
      case None =>
        -\/(NoRuleError)
    }
  }

  def findMatchingRule(apiGroup: ApiGroup, vertxRequest: HttpServerRequest): ApiError \/ RuleWithPathParams = {
    @tailrec def rec(basePath: BasePath, left: List[Rule]): Option[RuleWithPathParams] =
      left match {
        case rule :: tail =>
          RuleMatcher.makeMatch(vertxRequest.method(), Option(vertxRequest.path()).getOrElse(""), basePath, rule.conf.criteria) match {
            case Match(pathParams) => Some(RuleWithPathParams(rule, pathParams))
            case NoMatch => rec(basePath, tail)
          }
        case Nil => None
      }

    rec(apiGroup.matchCriteria.basePathResolved, apiGroup.rules) match {
      case Some(result) => \/-(result)
      case None         => -\/(NoRuleError)
    }
  }

  def withProxyHeaders(proxyHeaders: ProxyHeaders)(req: TargetRequest): TargetRequest =
    req.withHeaderValues(proxyHeaders.headers)
}

object ApiResponseHandler {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  import ApiHandler._
  import Responses._

  def handleApiError(ctx: TracingContext, requestSignature: String, vertxResponse: HttpServerResponse, apiError: ApiError): Unit = {
    apiError match {
      case NoRuleError =>
        log.debug(ctx, s"Rule not found, request='$requestSignature'")
        endWith(ctx, vertxResponse, Errors.ruleNotFound)
      case RequestPluginError(ex) =>
        log.error(ctx, s"Could not apply request plugin, request='$requestSignature'", ex)
        endWithException(ctx, vertxResponse, ex)
      case TargetApiError(ex) =>
        if (ex.getMessage().contains("timeout") && ex.getMessage().contains("exceeded")) {
          endWith(ctx, vertxResponse, Errors.responseTimeout)
        } else {
          log.error(ctx, s"Could not call target service, request='$requestSignature'", ex)
          endWith(ctx, vertxResponse, Errors.targetUnreachable)
        }
      case ResponsePluginError(ex) =>
        log.error(ctx, s"Could not apply response plugin, request='$requestSignature'", ex)
        endWithException(ctx, vertxResponse, ex)
    }
  }

  def handleApiResponse(ctx: TracingContext, requestSignature: String, vertxResponse: HttpServerResponse, apiResponse: ApiResponse): Unit = {
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

  def endWith(ctx: TracingContext, response: HttpServerResponse, error: Error): Unit = {
    if (!response.closed()) {
      val apiResponse = error.toApiResponse()
      for ((k, v) <- apiResponse.headers.toMap) response.putHeader(k, v.asJava)
      response.setStatusCode(apiResponse.statusCode).end(mkString(error.body))
    } else {
      log.debug(ctx, "Response already closed. Tried to end with error {}", error.toApiResponse())
    }
  }

  def endWithException(tracing: TracingContext, vertxResponse: HttpServerResponse, ex: Throwable): Unit = {
    def isEvenBusTimeout(ex: Throwable) =
      ex.isInstanceOf[ReplyException] && ex.getMessage().contains("Timed out")

    if (isEvenBusTimeout(ex) || (ex.getCause != null && isEvenBusTimeout(ex.getCause))) {
      endWith(tracing, vertxResponse, Errors.systemTimeout)
    } else {
      endWith(tracing, vertxResponse, Errors.unexpected)
    }
  }
}

object HttpConversions {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  def toRequestCtx(ctx: RoutingContext, tracingCtx: TracingContext, proxyHeaders: ProxyHeaders, ruleConf: RuleConf, basePath: BasePath, pathParams: PathParams, bodyOpt: Option[Buffer]): RequestCtx = {
    val req = ctx.request()
    val original = toOriginalRequest(req, pathParams, bodyOpt)
    val targetRequest =
      TargetRequest(
        method  = ruleConf.rewriteMethod.map(_.value).getOrElse(original.method),
        service = TargetService(ruleConf.target, req),
        uri     = chooseRelativeUri(tracingCtx, req, basePath, ruleConf, original),
        headers = removeHeadersAsProxy(ruleConf, original.headers),
        bodyOpt = bodyOpt
      )

    RequestCtx(
      correlationCtx = buildCorrelationCtx(ctx),
      tracingCtx = tracingCtx,
      request = targetRequest,
      original = original,
      proxyHeaders = proxyHeaders,
      properties = Properties(),
      authnCtx = AuthnCtx(),
      aborted = None
    )
  }

  def buildCorrelationCtx(ctx: RoutingContext): CorrelationCtx =
    CorrelationCtx.withFlowId(RoutingCtxData.getFlowId(ctx).value)

  def toOriginalRequest(req: HttpServerRequest, pathParams: PathParams, bodyOpt: Option[Buffer]): OriginalRequest =
    OriginalRequest(
      method      = req.method(),
      path        = UriPath(Option(req.path()).getOrElse("")),
      queryParams = toQueryParams(req.params()),
      headers     = toHeaders(req.headers()),
      bodyOpt     = bodyOpt,
      pathParams  = pathParams
    )

  def chooseRelativeUri(ctx: TracingContext, vertxRequest: HttpServerRequest, basePath: BasePath, ruleConf: RuleConf, original: OriginalRequest): RelativeUri = {
    ruleConf.rewritePath match {
      case Some(rewritePath) =>
        if (ruleConf.copyQueryOnRewrite.getOrElse(true))
          RewritableRelativeUri(rewritePath, original.queryParams, original.pathParams)
        else RewritableRelativeUri(rewritePath, QueryParams.of(), original.pathParams)
      case None =>
        val relativeOriginalPath =
          original.path.value.drop(basePath.value.length)

        val targetPath =
          if (ruleConf.dropPathPrefix) relativeOriginalPath.drop(ruleConf.criteria.path.prefix.value.length)
          else original.path.value
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

  def toHeaders(from: MultiMap): Headers =
    from.names().asScala.foldLeft(Headers()) { case (hs, name) =>
      hs.addValues(name, from.getAll(name).asScala.toList)
    }

  def toQueryParams(from: MultiMap): QueryParams =
    from.names().asScala.foldLeft(QueryParams()) { case (ps, name) =>
      ps.addValues(name, from.getAll(name).asScala.toList)
    }
}

