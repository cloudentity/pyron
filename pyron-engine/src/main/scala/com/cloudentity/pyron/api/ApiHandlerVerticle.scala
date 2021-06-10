package com.cloudentity.pyron.api

import com.cloudentity.pyron.api.HttpConversions.toRequestCtx
import com.cloudentity.pyron.api.ProxyHeadersHandler.getProxyHeaders
import com.cloudentity.pyron.api.Responses.Errors
import com.cloudentity.pyron.api.RoutingCtxData.getFlowState
import com.cloudentity.pyron.apigroup.{ApiGroup, ApiGroupConf, ApiGroupsChangeListener, ApiGroupsStore}
import com.cloudentity.pyron.client.TargetClient
import com.cloudentity.pyron.config.Conf
import com.cloudentity.pyron.domain.flow.{CallFailure, ProxyHeaders, RequestCtx, ResponseCtx}
import com.cloudentity.pyron.domain.http.{ApiResponse, CallOpts}
import com.cloudentity.pyron.domain.rule.{Kilobytes, RuleConf}
import com.cloudentity.pyron.plugin.PluginFunctions
import com.cloudentity.pyron.plugin.PluginFunctions.{RequestPlugin, ResponsePlugin}
import com.cloudentity.pyron.rule.{AppliedPathRewrite, Rule, RulesStore}
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

  override def apiGroupsChanged(groups: List[ApiGroup], confs: List[ApiGroupConf]): Unit =
    resetRulesAndSmartClients(groups)

  def handle(conf: Conf.AppConf, ctx: RoutingContext): VxFuture[Unit] = {
    val vertxRequest = ctx.request()
    val vertxResponse = ctx.response()
    val tracing: TracingContext = RoutingWithTracingS.getOrCreate(ctx, getTracing)
    val requestSignature = getRequestSignature(vertxRequest)

    log.debug(tracing, s"Received request: $requestSignature")
    log.trace(tracing, s"Received request: $requestSignature, headers=${vertxRequest.headers()}")

    VxFuture.succeededFuture(
      getProgram(conf.defaultRequestBodyMaxSize, ctx, tracing, requestSignature) map {
        case Some(apiResponse) => handleApiResponse(tracing, vertxResponse, apiResponse)
        case None => ()
      } recover { case ex: Throwable =>
        log.error(tracing, s"Unexpected error, request='$requestSignature'", ex)
        endWithException(tracing, vertxResponse, ex)
      }
    )
  }

  def getProgram(maxBodySize: Option[Kilobytes],
                 ctx: RoutingContext,
                 tracing: TracingContext,
                 requestSignature: String): Future[Option[ApiResponse]] = {

    findMatchingApiGroup(apiGroups, ctx.request()) flatMap { apiGroup =>
      findMatchingRule(apiGroup, ctx.request()).map(_ -> apiGroup)
    } match {
      case None => Future.successful(Some(Errors.ruleNotFound.toApiResponse()))
      case Some((appliedRule@AppliedRule(rule, rewrite), apiGroup)) =>
        log.debug(tracing, s"Found $rule for, request='$requestSignature'")
        setTracingOperationName(ctx, rule)
        addRule(ctx, rule)
        finish(ctx, tracing, appliedRule, apiGroup, maxBodySize)
    }
  }

  private def finish(ctx: RoutingContext,
                     tracing: TracingContext,
                     appliedRule: AppliedRule,
                     apiGroup: ApiGroup,
                     maxBodySize: Option[Kilobytes]): Future[Option[ApiResponse]] = {
    val AppliedRule(rule, rewrite) = appliedRule
    val proxyHeaders = getProxyHeaders(ctx).getOrElse(ProxyHeaders(Map(), ""))
    for {
      finalRequestCtx <- makeRequestCtx(ctx, tracing, proxyHeaders, rule, apiGroup, rewrite, maxBodySize)
      _ = setupRoutingCtxRequest(ctx, finalRequestCtx)
      finalResponseCtx <- makeResponseCtx(ctx, finalRequestCtx, tracing, rule)
      _ = setupRoutingCtxResponse(ctx, finalResponseCtx)
    } yield Some(finalResponseCtx.response)
  }

  def makeRequestCtx(ctx: RoutingContext,
                     tracing: TracingContext,
                     proxyHeaders: ProxyHeaders,
                     rule: Rule,
                     apiGroup: ApiGroup,
                     appliedRewrite: AppliedPathRewrite,
                     maxBodySize: Option[Kilobytes]): Future[RequestCtx] =
    for {
      initReqCtx <- toRequestCtx(ctx, tracing, apiGroup, rule.conf, appliedRewrite.pathParams, proxyHeaders, maxBodySize)
      reqCtxWithProxyHeaders = initReqCtx.modifyRequest(withProxyHeaders(proxyHeaders))
      finalRequestCtx <- applyRequestPlugins(reqCtxWithProxyHeaders, rule.requestPlugins)
    } yield finalRequestCtx

  def makeResponseCtx(ctx: RoutingContext,
                      requestCtx: RequestCtx,
                      tracing: TracingContext,
                      rule: Rule): Future[ResponseCtx] =
    for {
      (initResponse, fail) <- requestCtx.aborted.fold {
        callTarget(requestCtx, tracing, rule.conf.call)
      } { response =>
        Future.successful((response, false))
      }
      modifiedResponse <- requestCtx.modifyResponse(initResponse)
      allResponsePlugins = getFlowState(ctx).rules.flatMap(_.responsePlugins)
      finalResponseCtx <- applyResponsePlugins(toResponseCtx(requestCtx, fail, modifiedResponse), allResponsePlugins)
    } yield finalResponseCtx

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

  def setupRoutingCtxRequest(ctx: RoutingContext, requestCtx: RequestCtx): Unit = {
    setAuthnCtx(ctx, requestCtx.authnCtx)
    setAborted(ctx, requestCtx.aborted.isDefined)
    setFailure(ctx, requestCtx.failed)
    addExtraAccessLogItems(ctx, requestCtx.accessLog)
    addProperties(ctx, requestCtx.properties)
  }

  def setupRoutingCtxResponse(ctx: RoutingContext, responseCtx: ResponseCtx): Unit = {
    addExtraAccessLogItems(ctx, responseCtx.accessLog)
    addProperties(ctx, responseCtx.properties)
  }

  def callTarget(requestCtx: RequestCtx, tracing: TracingContext, callOpts: Option[CallOpts]): Future[(ApiResponse, Boolean)] =
    targetClient.call(tracing, requestCtx.targetRequest, requestCtx.bodyStreamOpt, callOpts).map {
      case \/-(response) => (HttpConversions.toApiResponse(response), false)
      case -\/(ex) => (mapTargetClientException(tracing, ex), true)
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

  def getRequestSignature(vertxRequest: HttpServerRequest): String =
    Option(vertxRequest.host()) match {
      case Some(_) => s"${vertxRequest.method()} ${vertxRequest.uri()}"
      case None => s"${vertxRequest.method()} ${vertxRequest.absoluteURI()}"
    }

}