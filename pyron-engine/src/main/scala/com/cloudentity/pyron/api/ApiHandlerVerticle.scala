package com.cloudentity.pyron.api

import com.cloudentity.pyron.api.HttpConversions.toRequestCtx
import com.cloudentity.pyron.api.ProxyHeadersHandler.getProxyHeaders
import com.cloudentity.pyron.api.Responses.Errors
import com.cloudentity.pyron.apigroup.{ApiGroup, ApiGroupConf, ApiGroupsChangeListener, ApiGroupsStore}
import com.cloudentity.pyron.client.TargetClient
import com.cloudentity.pyron.config.Conf
import com.cloudentity.pyron.domain.flow.{CallFailure, ProxyHeaders, RequestCtx, ResponseCtx}
import com.cloudentity.pyron.domain.http.{ApiResponse, CallOpts}
import com.cloudentity.pyron.domain.rule.{Kilobytes, RuleConf}
import com.cloudentity.pyron.plugin.PluginFunctions
import com.cloudentity.pyron.plugin.PluginFunctions.{RequestPlugin, ResponsePlugin}
import com.cloudentity.pyron.rule.{AppliedRewrite, Rule, RulesStore}
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

  override def apiGroupsChanged(groups: List[ApiGroup], confs: List[ApiGroupConf]): Unit =
    resetRulesAndSmartClients(groups)

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
    val tracing: TracingContext = RoutingWithTracingS.getOrCreate(ctx, getTracing)
    val requestSignature = getRequestSignature(vertxRequest)

    log.debug(tracing, s"Received request: $requestSignature")
    log.trace(tracing, s"Received request: $requestSignature, headers=${vertxRequest.headers()}")

    getProgram(defaultRequestBodyMaxSize, ctx, tracing, requestSignature)
      .map {
        case Some(apiResponse) => handleApiResponse(tracing, vertxResponse, apiResponse)
        case None => ()
      }.recover { case ex: Throwable =>
        log.error(tracing, s"Unexpected error, request='$requestSignature'", ex)
        endWithException(tracing, vertxResponse, ex)
      }

    VxFuture.succeededFuture(())
  }

  def getProgram(maxBodySize: Option[Kilobytes],
                 ctx: RoutingContext,
                 tracing: TracingContext,
                 requestSignature: String): Future[Option[ApiResponse]] = {

    findMatchingApiGroup(apiGroups, ctx.request()) flatMap { apiGroup =>
      findMatchingRule(apiGroup, ctx.request()).map(_ -> apiGroup)
    } match {
      case None => Future.successful(Some(Errors.ruleNotFound.toApiResponse()))
      case Some((appliedRule @ AppliedRule(rule, rewrite), apiGroup)) =>
        log.debug(tracing, s"Found $rule for, request='$requestSignature'")

        addRule(ctx, rule)
        setTracingOperationName(ctx, rule)

        if (rule.conf.reroute) {
          reroute(ctx, appliedRule)
          Future.successful(None)
        } else {
          val proxyHeaders = getProxyHeaders(ctx).getOrElse(ProxyHeaders(Map(), ""))
          for {
            finalRequestCtx <- getFinalRequestCtx(maxBodySize, ctx, tracing, proxyHeaders, rule, apiGroup, rewrite)
            finalResponseCtx <- getFinalResponseCtx(ctx, tracing, rule, finalRequestCtx)
          } yield Some(finalResponseCtx.response)
        }

    }
  }

  private def reroute(ctx: RoutingContext, appliedRule: AppliedRule): HttpServerRequest = {
    appliedRule.rule.conf.rewriteMethod.fold {
      ctx.reroute(appliedRule.appliedRewrite.targetPath)
    } { method => ctx.reroute(method.value, appliedRule.appliedRewrite.targetPath) }
    ctx.request().pause()
  }

  def getFinalRequestCtx(maxBodySize: Option[Kilobytes],
                         ctx: RoutingContext,
                         tracing: TracingContext,
                         proxyHeaders: ProxyHeaders,
                         rule: Rule,
                         apiGroup: ApiGroup,
                         appliedRewrite: AppliedRewrite): Future[RequestCtx] = for {
    initRequestCtx <- toRequestCtx(
      maxBodySize, ctx, tracing, proxyHeaders, rule.conf, apiGroup, appliedRewrite.pathParams)
    finalRequestCtx <- applyRequestPlugins(
      initRequestCtx.modifyRequest(withProxyHeaders(proxyHeaders)),
      rule.requestPlugins
    ) map (setupFinalRequest(ctx, _))
  } yield finalRequestCtx

  def getFinalResponseCtx(ctx: RoutingContext,
                          tracing: TracingContext,
                          rule: Rule,
                          requestCtx: RequestCtx): Future[ResponseCtx] = for {

    (initResponse, fail) <- requestCtx.aborted.fold {
      callTarget(requestCtx, tracing, rule.conf.call)
    } { response => Future.successful((response, false)) }

    modifiedResponse <- requestCtx.modifyResponse(initResponse)
    finalResponseCtx <- applyResponsePlugins(
      toResponseCtx(requestCtx, fail, modifiedResponse),
      rule.responsePlugins
    ).map(setupFinalResponse(ctx, _))
  } yield finalResponseCtx


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

  def callTarget(requestCtx: RequestCtx, tracing: TracingContext, callOpts: Option[CallOpts]): Future[(ApiResponse, Boolean)] =
    targetClient.call(tracing, requestCtx.request, requestCtx.bodyStreamOpt, callOpts).map {
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

}
