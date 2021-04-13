package com.cloudentity.pyron.api

import com.cloudentity.pyron.api.Responses.Errors
import com.cloudentity.pyron.apigroup.{ApiGroup, ApiGroupConf, ApiGroupsChangeListener, ApiGroupsStore}
import com.cloudentity.pyron.client.TargetClient
import com.cloudentity.pyron.config.Conf
import com.cloudentity.pyron.domain.flow.{CallFailure, ProxyHeaders, RequestCtx, ResponseCtx}
import com.cloudentity.pyron.domain.http.{ApiResponse, CallOpts}
import com.cloudentity.pyron.domain.rule.{Kilobytes, RuleConf}
import com.cloudentity.pyron.plugin.PluginFunctions
import com.cloudentity.pyron.plugin.PluginFunctions.{RequestPlugin, ResponsePlugin}
import com.cloudentity.pyron.rule.{Rule, RulesStore}
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.server.api.RouteHandler
import com.cloudentity.tools.vertx.server.api.tracing.RoutingWithTracingS
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.config.ConfigChange
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.{Future => VxFuture}
import io.vertx.ext.web.RoutingContext
import scalaz.{-\/, \/-}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class ApiHandlerVerticle extends ScalaServiceVerticle with ApiHandler with ApiGroupsChangeListener {

  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  import ApiRequestHandler._
  import ApiResponseHandler._

  var targetClient: TargetClient = _
  var apiGroups: List[ApiGroup] = List()
  var rulesStore: RulesStore = _

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

  def resetRulesAndSmartClients(gs: List[ApiGroup]): Future[Unit] = {
    val ruleConfs: List[RuleConf] = gs.flatMap(_.rules).map(_.conf)
    TargetClient.resetTargetClient(
      vertx = vertx,
      confService = getConfService,
      tracing = getTracing,
      rules = ruleConfs,
      oldTargetClientOpt = Option(targetClient)
    ).map { newTargetClient =>
      apiGroups = gs
      targetClient = newTargetClient
    }
  }

  def handle(defaultRequestBodyMaxSize: Option[Kilobytes], ctx: RoutingContext): VxFuture[Unit] = {
    val vertxRequest = ctx.request()
    val vertxResponse = ctx.response()
    val tracingContext: TracingContext = RoutingWithTracingS.getOrCreate(ctx, getTracing)
    val requestSignature = getRequestSignature(vertxRequest)

    log.debug(tracingContext, s"Received request: $requestSignature")
    log.trace(tracingContext, s"Received request: $requestSignature, headers=${vertxRequest.headers()}")

    getProgram(defaultRequestBodyMaxSize, ctx, tracingContext, requestSignature).onComplete { result =>
      try {
        result match {
          case Success(apiResponse) =>
            handleApiResponse(tracingContext, vertxResponse, apiResponse)
          case Failure(ex) =>
            log.error(tracingContext, s"Unexpected error, request='$requestSignature'", ex)
            endWithException(tracingContext, vertxResponse, ex)
        }
      } catch {
        case ex: Throwable =>
          log.error(tracingContext, s"Unexpected error, request='$requestSignature'", ex)
          endWithException(tracingContext, vertxResponse, ex)
      }
    }

    VxFuture.succeededFuture(())
  }

  def getProgram(defaultRequestBodyMaxSize: Option[Kilobytes],
                 ctx: RoutingContext,
                 tracingContext: TracingContext,
                 requestSignature: String): Future[ApiResponse] = {

    val matchingApiGroupAndRuleOpt = for {
      apiGroup <- findMatchingApiGroup(apiGroups, ctx.request())
      ruleWithPathParams <- findMatchingRule(apiGroup, ctx.request())
    } yield (apiGroup, ruleWithPathParams)

    matchingApiGroupAndRuleOpt match {
      case None => Future.successful(Errors.ruleNotFound.toApiResponse())
      case Some((apiGroup, ruleWithPathParams)) =>
        val rule = ruleWithPathParams.rule
        val pathParams = ruleWithPathParams.appliedRewrite.pathParams
        val proxyHeaders = getProxyHeaders(ctx)

        setRule(ctx, rule)
        setTracingOperationName(ctx, rule)
        log.debug(tracingContext, s"Found $rule for, request='$requestSignature'")

        for {
          initRequestCtx <- HttpConversions.toRequestCtx(
            defaultRequestBodyMaxSize,
            ctx,
            tracingContext,
            proxyHeaders,
            rule.conf,
            apiGroup,
            pathParams
          )

          finalRequestCtx <- applyRequestPlugins(
            initRequestCtx.modifyRequest(withProxyHeaders(proxyHeaders)),
            rule.requestPlugins
          ).map(setupFinalRequest(ctx, _))

          (initResponse, fail) <- finalRequestCtx.aborted match {
            case None => callTarget(tracingContext, finalRequestCtx, rule.conf.call)
            case Some(response) => Future.successful((response, false))
          }

          modifiedResponse <- finalRequestCtx.modifyResponse(initResponse)
          responseCtx = toResponseCtx(finalRequestCtx, fail, modifiedResponse)
          finalResponseCtx <- applyResponsePlugins(responseCtx, rule.responsePlugins)
            .map(setupFinalResponse(ctx, _))

        } yield finalResponseCtx.response
    }
  }

  def applyRequestPlugins(requestCtx: RequestCtx, plugins: List[RequestPlugin]): Future[RequestCtx] =
    PluginFunctions.applyRequestPlugins(requestCtx, plugins) { ex =>
      log.error(requestCtx.tracingCtx, s"Could not apply request plugin", ex)
      exceptionToApiResponse(ex)
    }

  def setupFinalRequest(ctx: RoutingContext, requestCtx: RequestCtx): RequestCtx = {
    setAuthnCtx(ctx, requestCtx.authnCtx)
    setAborted(ctx, requestCtx.aborted.isDefined)
    setFailure(ctx, requestCtx.failed)
    addExtraAccessLogItems(ctx, requestCtx.accessLog)
    addProperties(ctx, requestCtx.properties)
    requestCtx
  }

  def applyResponsePlugins(responseCtx: ResponseCtx, plugins: List[ResponsePlugin]): Future[ResponseCtx] =
    PluginFunctions.applyResponsePlugins(responseCtx, plugins) { ex =>
      log.error(responseCtx.tracingCtx, s"Could not apply response plugin", ex)
      exceptionToApiResponse(ex)
    }

  def setupFinalResponse(ctx: RoutingContext, responseCtx: ResponseCtx): ResponseCtx = {
    addExtraAccessLogItems(ctx, responseCtx.accessLog)
    addProperties(ctx, responseCtx.properties)
    responseCtx
  }

  def callTarget(tracing: TracingContext, ctx: RequestCtx, callOpts: Option[CallOpts]): Future[(ApiResponse, Boolean)] =
    targetClient.call(tracing, ctx.request, ctx.bodyStreamOpt, callOpts).map {
      case \/-(response) => (HttpConversions.toApiResponse(response), false)
      case -\/(ex) => (mapTargetClientException(tracing, ex), true)
    }

  def setTracingOperationName(ctx: RoutingContext, rule: Rule): Unit = {
    val name = s"${rule.conf.criteria.method} ${rule.conf.criteria.rewrite.matchPattern}"
    ctx.put(RouteHandler.urlPathKey, rule.conf.criteria.rewrite.matchPattern)
    RoutingWithTracingS.getOrCreate(ctx, getTracing).setOperationName(name)
  }

  def toResponseCtx(requestCtx: RequestCtx, targetCallFailed: Boolean, response: ApiResponse): ResponseCtx = {
    val targetResponse = if (requestCtx.isAborted() || targetCallFailed) None else Some(response)
    val failed = if (targetCallFailed) Some(CallFailure) else requestCtx.failed

    ResponseCtx(
      targetResponse,
      response, requestCtx.request, requestCtx.original, requestCtx.tracingCtx,
      requestCtx.properties, requestCtx.authnCtx, requestCtx.accessLog, requestCtx.aborted.isDefined, failed
    )
  }

  def getRequestSignature(vertxRequest: HttpServerRequest): String = Option(vertxRequest.host()) match {
    case Some(_) => s"${vertxRequest.method()} ${vertxRequest.uri()}"
    case None => s"${vertxRequest.method()} ${vertxRequest.absoluteURI()}"
  }

  def getProxyHeaders(ctx: RoutingContext): ProxyHeaders =
    ProxyHeadersHandler.getProxyHeaders(ctx).getOrElse(ProxyHeaders(Map(), ""))
}
