package com.cloudentity.pyron.plugin.impl.transform.response

import com.cloudentity.pyron.domain.flow.{PluginName, ResponseCtx}
import com.cloudentity.pyron.plugin.config.{ValidateOk, ValidateResponse}
import com.cloudentity.pyron.plugin.impl.transform.TransformHeaders.transformResHeaders
import com.cloudentity.pyron.plugin.impl.transform.TransformJsonBody.transformResJsonBody
import com.cloudentity.pyron.plugin.impl.transform.TransformHttpStatus.transformHttpStatus
import com.cloudentity.pyron.plugin.impl.transform._
import com.cloudentity.pyron.plugin.util.value.ValueResolver
import com.cloudentity.pyron.plugin.verticle.ResponsePluginVerticle
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.vertx.core.json.JsonObject

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class TransformResponsePluginVerticleConf(conf: Option[JsonObject])

class TransformResponsePlugin  extends ResponsePluginVerticle[ResponseTransformerConf] {

  override def name: PluginName = PluginName("transform-response")

  var verticleConf: TransformResponsePluginVerticleConf = _

  override def initService(): Unit = {
    // if PLUGIN_TRANSFORM_RESPONSE_CONF_REF in plugin/transform-response.json is not set
    // then verticleConfig.conf gets resolved to empty string "" which cause deserialization issues
    // below Decoder[Option[JsonObject]] treats empty string as None
    implicit val confDecoder: Decoder[Option[JsonObject]] =
    Decoder.decodeOption[JsonObject].or {
      Decoder.decodeString.emap {
        case "" => Right(None)
        case x => Left("could not decode optional JsonObject")
      }
    }
    verticleConf = decodeConfigUnsafe(deriveDecoder[TransformResponsePluginVerticleConf])
  }

  override def apply(ctx: ResponseCtx, conf: ResponseTransformerConf): Future[ResponseCtx] = Future.successful {
    val respJsonBodyOpt: Option[JsonObject] = parseResponseJsonBodyIfRequired(ctx, conf)
    val reqJsonBodyOpt: Option[JsonObject] = parseRequestJsonBodyIfRequired(ctx, conf)
    ctx |>
      transformResJsonBody(resolveBodyOps(ctx, conf.body, reqJsonBodyOpt, respJsonBodyOpt), respJsonBodyOpt) |>
      transformResHeaders(resolveHeaderOps(ctx, conf.headers, reqJsonBodyOpt, respJsonBodyOpt))|>
      transformHttpStatus(conf.status)
  }

  // to avoid parsing request body to JsonObject over and over again we do it only if JSON body reference exists or body operations are set
  def parseRequestJsonBodyIfRequired(ctx: ResponseCtx, conf: ResponseTransformerConf): Option[JsonObject] = {
    if (conf.parseRequestJsonBody)
      Try(ctx.targetRequest.bodyOpt.map(_.toJsonObject)) match {
        case Success(jsonBodyOpt) => jsonBodyOpt
        case Failure(ex) =>
          log.error(ctx.tracingCtx, "Could not parse JSON request body", ex)
          None
      }
    else None
  }
  // to avoid parsing response body to JsonObject over and over again we do it only if JSON body reference exists or body operations are set
  def parseResponseJsonBodyIfRequired(ctx: ResponseCtx, conf: ResponseTransformerConf): Option[JsonObject] = {
    if(emptyApiResponseBody(ctx)) Some(new JsonObject())
    else if (conf.parseResponseJsonBody) {
      try {
        Some(ctx.response.body.toJsonObject)
      } catch {
        case ex: Throwable =>
          log.error(ctx.tracingCtx, "Could not parse JSON response body", ex)
          None
      }
    }
    else None
  }

  def emptyApiResponseBody(ctx: ResponseCtx): Boolean = ctx.response.body.toString().isEmpty

  private def confValues(): JsonObject =
    verticleConf.conf.getOrElse(new JsonObject())

  def resolveBodyOps(ctx: ResponseCtx, bodyOps: BodyOps, reqJsonBodyOpt: Option[JsonObject], respJsonBodyOpt: Option[JsonObject]): ResolvedBodyOps =
    ResolvedBodyOps(bodyOps.set.map(_.mapValues(ValueResolver.resolveJson(ctx, reqJsonBodyOpt, respJsonBodyOpt, confValues(), _))), bodyOps.remove, bodyOps.drop, bodyOps.nullIfAbsent)

  def resolveHeaderOps(ctx: ResponseCtx, headerOps: HeaderOps, reqJsonBodyOpt: Option[JsonObject], respJsonBodyOpt: Option[JsonObject]): ResolvedHeaderOps =
    ResolvedHeaderOps(headerOps.set.map(_.mapValues(ValueResolver.resolveListOfStrings(ctx, reqJsonBodyOpt, respJsonBodyOpt, confValues(), _))))

  override def validate(conf: ResponseTransformerConf): ValidateResponse = ValidateOk

  override def confDecoder: Decoder[ResponseTransformerConf] = ResponseTransformerConf.TransformerConfDecoder
}
