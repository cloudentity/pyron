package com.cloudentity.pyron.plugin.impl.transform.response

import com.cloudentity.pyron.domain.flow.{PluginName, ResponseCtx}
import com.cloudentity.pyron.plugin.config.{ValidateOk, ValidateResponse}
import com.cloudentity.pyron.plugin.impl.transform.TransformHeaders.transformResHeaders
import com.cloudentity.pyron.plugin.impl.transform.TransformJsonBody.{transformReqJsonBody, transformResJsonBody}
import com.cloudentity.pyron.plugin.impl.transform._
import com.cloudentity.pyron.plugin.util.value.ValueResolver
import com.cloudentity.pyron.plugin.verticle.ResponsePluginVerticle
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.vertx.core.json.JsonObject

import scala.concurrent.Future

case class TransformResponsePluginVerticleConf(conf: Option[JsonObject])

class TransformResponsePlugin  extends ResponsePluginVerticle[TransformerConf] {

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

  override def apply(ctx: ResponseCtx, conf: TransformerConf): Future[ResponseCtx] = Future.successful {
    val jsonBodyOpt: Option[JsonObject] = parseJsonBodyIfRequired(ctx, conf)
    ctx |>
      transformResJsonBody(resolveBodyOps(ctx, conf.body, jsonBodyOpt), jsonBodyOpt) |>
      transformResHeaders(resolveHeaderOps(ctx, conf.headers, jsonBodyOpt))
  }

  // to avoid parsing body to JsonObject over and over again we do it only if JSON body reference exists or body operations are set
  def parseJsonBodyIfRequired(ctx: ResponseCtx, conf: TransformerConf): Option[JsonObject] = {
    if (conf.parseJsonBody) {
      try {
        Some(ctx.response.body.toJsonObject)
      } catch {
        case ex: Throwable =>
          log.error(ctx.tracingCtx, "Could not parse JSON body", ex)
          None
      }
    }
    else None
  }

  private def confValues(): JsonObject =
    verticleConf.conf.getOrElse(new JsonObject())

  def resolveBodyOps(ctx: ResponseCtx, bodyOps: BodyOps, jsonBodyOpt: Option[JsonObject]): ResolvedBodyOps =
    ResolvedBodyOps(bodyOps.set.map(_.mapValues(ValueResolver.resolveJson(ctx, jsonBodyOpt, confValues(), _))), bodyOps.drop)

  def resolveHeaderOps(ctx: ResponseCtx, headerOps: HeaderOps, jsonBodyOpt: Option[JsonObject]): ResolvedHeaderOps =
    ResolvedHeaderOps(headerOps.set.map(_.mapValues(ValueResolver.resolveListOfStrings(ctx, jsonBodyOpt, confValues(), _))))

  override def validate(conf: TransformerConf): ValidateResponse = ValidateOk

  override def confDecoder: Decoder[TransformerConf] = TransformerConf.TransformerConfDecoder
}
