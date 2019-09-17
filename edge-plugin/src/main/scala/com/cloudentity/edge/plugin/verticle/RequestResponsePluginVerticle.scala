package com.cloudentity.edge.plugin.verticle

import com.cloudentity.tools.vertx.scala.Futures
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.circe.Decoder.Result
import io.circe.Json
import com.cloudentity.edge.plugin.openapi.ConvertOpenApiRequest
import com.cloudentity.edge.plugin.config.ValidateRequest
import com.cloudentity.edge.domain.flow.{PluginName, RequestCtx, ResponseCtx}
import com.cloudentity.edge.domain.rule.RuleConfWithPlugins
import com.cloudentity.edge.plugin._
import com.cloudentity.edge.plugin.bus.{request, response}
import com.cloudentity.edge.plugin.config.ValidateResponse
import com.cloudentity.edge.plugin.openapi.ConvertOpenApiResponse
import com.cloudentity.edge.util.ConfigDecoder
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * @tparam C plugin configuration (e.g. case class AuthzPluginConf(policy: String))
  */
abstract class RequestResponsePluginVerticle[C] extends ScalaServiceVerticle with PluginService with RequestPluginService
  with ResponsePluginService with ValidatePluginService with ConvertOpenApiService with PluginConfValidator[C]
  with PluginRulesExtender[C] with PluginOpenApiConverter[C] with ConfigDecoder {

  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)
  var confCache: Map[Json, C] = Map()

    // TO IMPLEMENT
  def name: PluginName
  def apply(requestCtx: RequestCtx, conf: C): Future[RequestCtx]
  def apply(responseCtx: ResponseCtx, conf: C): Future[ResponseCtx]
  def validate(conf: C): ValidateResponse

  // ServiceVerticle IMPLEMENTATION
  override protected def vertxServiceAddressPrefixS: Option[String] = Some {
      super.vertxServiceAddressPrefixS.getOrElse(name.value)
    }
  override def vertxService(): Class[_] = classOf[RequestPluginService]

  override def applyPlugin(ctx: TracingContext, req: request.ApplyRequest): VxFuture[request.ApplyResponse] =
    Futures.toJava(handleApply(ctx, req))

  override def applyPlugin(ctx: TracingContext, req: response.ApplyRequest): VxFuture[response.ApplyResponse] =
    Futures.toJava(handleApply(ctx, req))

  override def validateConfig(req: ValidateRequest): VxFuture[ValidateResponse] =
    Futures.toJava(handleValidate(req))

  override def extendRules(rule: RuleConfWithPlugins, pluginConf: Json): VxFuture[ExtendRules] =
    handleExtendRules(rule, pluginConf)

  def convertOpenApi(ctx: TracingContext, req: ConvertOpenApiRequest): VxFuture[ConvertOpenApiResponse] =
    Futures.toJava(handleConvertOpenApi(req))

  // PLUGIN IMPLEMENTATION
  def isReady(): VxFuture[java.lang.Boolean] =
    Futures.toJava(Future.successful(true))

  // REQUEST PLUGIN IMPLEMENTATION
  def handleApply(tracingCtx: TracingContext, req: request.ApplyRequest): Future[request.ApplyResponse] = {
    tracingCtx.setOperationName(s"${name.value} request plugin")
    getFromCacheOrDecode(req.conf.conf) match {
      case Right(conf) =>
        confCache = confCache.updated(req.conf.conf, conf)
        tryApply(req.ctx, tracingCtx, conf)
      case Left(ex) =>
        log.error(tracingCtx, s"Error on decoding plugin config for ${req}", ex)
        tracingCtx.logException(ex)
        Future.successful(request.ApplyError(ex.message))
    }
  }

  protected def tryApply(ctx: RequestCtx, tracingCtx: TracingContext, conf: C): Future[request.ApplyResponse] = {
    val parentTracingCtx = ctx.tracingCtx
    Try(apply(ctx.withTracingCtx(tracingCtx), conf)) match {
      case Success(future) =>
        future
          .map(ctx => request.Continue(ctx.withTracingCtx(parentTracingCtx)))
          .recover { case ex: Throwable =>
            log.error(tracingCtx, s"RequestPluginVerticle.apply returned failed Future for $ctx", ex)
            ctx.tracingCtx.logError(ex)
            request.ApplyError(ex.getMessage)
          }
      case Failure(ex) =>
        log.error(tracingCtx, s"RequestPluginVerticle.apply threw exception for $ctx", ex)
        ctx.tracingCtx.logException(ex)
        Future.successful(request.ApplyError(ex.getMessage))
    }
  }

  // RESPONSE PLUGIN IMPLEMENTATION
  def handleApply(tracingCtx: TracingContext, resp: response.ApplyRequest): Future[response.ApplyResponse] = {
    tracingCtx.setOperationName(s"${name.value} response plugin")
    getFromCacheOrDecode(resp.conf.conf) match {
      case Right(conf) =>
        confCache = confCache.updated(resp.conf.conf, conf)
        tryApply(resp.ctx, tracingCtx, conf)
      case Left(ex) =>
        log.error(tracingCtx, s"Error on decoding plugin config for ${resp}", ex)
        tracingCtx.logException(ex)
        Future.successful(response.ApplyError(ex.message))
    }
  }

  protected def tryApply(ctx: ResponseCtx, tracingCtx: TracingContext, conf: C): Future[response.ApplyResponse] = {
    val parentTracingCtx = ctx.tracingCtx
    Try(apply(ctx.withTracingCtx(tracingCtx), conf)) match {
      case Success(future) =>
        future
          .map(ctx => response.Continue(ctx.withTracingCtx(parentTracingCtx)))
          .recover { case ex: Throwable =>
            log.error(tracingCtx, s"ResponsePluginVerticle.apply returned failed Future for $ctx", ex)
            ctx.tracingCtx.logError(ex)
            response.ApplyError(ex.getMessage)
          }
      case Failure(ex) =>
        log.error(tracingCtx, s"ResponsePluginVerticle.apply threw exception for $ctx", ex)
        ctx.tracingCtx.logException(ex)
        Future.successful(response.ApplyError(ex.getMessage))
    }
  }

  protected def getFromCacheOrDecode(conf: Json): Result[C] =
    confCache.get(conf) match {
      case Some(ruleConf) => Right(ruleConf)
      case None           => decodeRuleConf(conf)
    }
}