package com.cloudentity.pyron.plugin.verticle

import com.cloudentity.tools.vertx.scala.Futures
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.circe.{Decoder, Encoder, Json}
import com.cloudentity.pyron.plugin.openapi.ConvertOpenApiRequest
import com.cloudentity.pyron.plugin.config.ValidateRequest
import com.cloudentity.pyron.domain.flow.{PluginName, RequestCtx, ResponseCtx}
import com.cloudentity.pyron.domain.rule.RuleConfWithPlugins
import com.cloudentity.pyron.plugin._
import com.cloudentity.pyron.plugin.bus.{request, response}
import com.cloudentity.pyron.plugin.condition.ApplyIf
import com.cloudentity.pyron.plugin.config.ValidateResponse
import com.cloudentity.pyron.plugin.openapi.ConvertOpenApiResponse
import com.cloudentity.pyron.util.ConfigDecoder
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.json.JsonObject
import io.vertx.core.{Future => VxFuture}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * @tparam C plugin configuration (e.g. case class AuthzPluginConf(policy: String))
  */
abstract class RequestResponsePluginVerticle[C] extends ScalaServiceVerticle with PluginService with RequestPluginService
  with ResponsePluginService with ValidatePluginService with ConvertOpenApiService with PluginConfValidator[C]
  with PluginRulesExtender[C] with PluginOpenApiConverter[C] with ConfigDecoder {

  private lazy val loggerName = this.getClass + Try(vertxServiceAddressPrefixS.filter(_ != name.value).map(":" + _).get).getOrElse("")
  lazy val log: LoggingWithTracing = LoggingWithTracing.getLogger(loggerName)
  var confCache: Map[Json, C] = Map()
  var applyIfCache: Map[Json, ApplyIf] = Map()

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

  override def validateApplyIf(req: ValidateRequest): VxFuture[ValidateResponse] =
    VxFuture.succeededFuture {
      req.conf.applyIf.map(decodeApplyIf)
        .map(_.fold(error => ValidateResponse.failure("Invalid `applyIf` condition: " + error.getMessage), _ => ValidateResponse.ok()))
        .getOrElse(ValidateResponse.ok())
    }

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
    getConfFromCacheOrDecode(req.conf.conf) match {
      case Right(conf) =>
        tryApply(req.ctx, tracingCtx, conf)
      case Left(ex) =>
        log.error(tracingCtx, s"Error on decoding plugin config for ${req}", ex)
        tracingCtx.logException(ex)
        Future.successful(request.ApplyError(ex))
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
            request.ApplyError(ex)
          }
      case Failure(ex) =>
        log.error(tracingCtx, s"RequestPluginVerticle.apply threw exception for $ctx", ex)
        ctx.tracingCtx.logException(ex)
        Future.successful(request.ApplyError(ex))
    }
  }

  // RESPONSE PLUGIN IMPLEMENTATION
  def handleApply(tracingCtx: TracingContext, resp: response.ApplyRequest): Future[response.ApplyResponse] = {
    tracingCtx.setOperationName(s"${name.value} response plugin")
    getFromCacheOrDecode(resp.conf.conf, resp.conf.applyIf) match {
      case Right((conf, applyIf)) =>
        if (ApplyIf.evaluate(applyIf, resp.ctx)) tryApply(resp.ctx, tracingCtx, conf)
        else Future.successful(response.Continue(resp.ctx))
      case Left(ex) =>
        log.error(tracingCtx, s"Error on decoding plugin config for ${resp}", ex)
        tracingCtx.logException(ex)
        Future.successful(response.ApplyError(ex))
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
            response.ApplyError(ex)
          }
      case Failure(ex) =>
        log.error(tracingCtx, s"ResponsePluginVerticle.apply threw exception for $ctx", ex)
        ctx.tracingCtx.logException(ex)
        Future.successful(response.ApplyError(ex))
    }
  }

  protected def getConfFromCacheOrDecode(conf: Json): Either[Throwable, C] =
    confCache.get(conf).map(Right(_))
      .getOrElse(decodeRuleConf(conf).map(tapCacheUpdate(x => confCache = confCache.updated(conf, x))))

  protected def getApplyIfFromCacheOrDecode(applyIf: Json): Either[Throwable, ApplyIf] =
    applyIfCache.get(applyIf).map(Right(_))
      .getOrElse(decodeApplyIf(applyIf).map(tapCacheUpdate(c => applyIfCache = applyIfCache.updated(applyIf, c))))

  protected def getFromCacheOrDecode(confRaw: Json, applyIfRawOpt: Option[Json]): Either[Throwable, (C, ApplyIf)] =
    for {
      conf     <- getConfFromCacheOrDecode(confRaw)
      applyIf  <- applyIfRawOpt.map(getApplyIfFromCacheOrDecode).getOrElse(Right(ApplyIf.Always))
    } yield (conf, applyIf)

  private def tapCacheUpdate[A](updateCache: A => Unit)(a: A): A = {
    updateCache(a)
    a
  }

  protected def decodeApplyIf(applyIf: Json): Either[Throwable, ApplyIf] = ApplyIf.ApplyIfDecoder.decodeJson(applyIf)

  implicit val JsonObjectDecoder: Decoder[io.vertx.core.json.JsonObject] =
    Decoder.decodeJson.emapTry(o => Try(new io.vertx.core.json.JsonObject(o.noSpaces)))

  implicit val JsonObjectEncoder: Encoder[io.vertx.core.json.JsonObject] =
    (o: JsonObject) => io.circe.parser.parse(o.toString).getOrElse(throw new Exception(s"could not encode '${o.toString}' to JSON"))
}