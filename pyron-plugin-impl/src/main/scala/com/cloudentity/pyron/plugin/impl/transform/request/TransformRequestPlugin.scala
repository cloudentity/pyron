package com.cloudentity.pyron.plugin.impl.transform.request

import com.cloudentity.pyron.domain.flow.{PluginName, RequestCtx}
import com.cloudentity.pyron.domain.openapi.OpenApiRule
import com.cloudentity.pyron.plugin.config._
import com.cloudentity.pyron.plugin.impl.transform.TransformHeaders.transformReqHeaders
import com.cloudentity.pyron.plugin.impl.transform.TransformJsonBody.transformReqJsonBody
import com.cloudentity.pyron.plugin.impl.transform._
import com.cloudentity.pyron.plugin.openapi._
import com.cloudentity.pyron.plugin.util.value.{Path, ValueResolver}
import com.cloudentity.pyron.plugin.util.value.ValueResolver.resolveJson
import com.cloudentity.pyron.plugin.verticle.RequestPluginVerticle
import io.circe.Decoder
import io.circe.generic.semiauto._
import io.swagger.models.Swagger
import io.vertx.core.json.JsonObject

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class TransformRequestPluginVerticleConf(conf: Option[JsonObject])

class TransformRequestPlugin extends RequestPluginVerticle[TransformerConf]
  with TransformPathParams with TransformQueryParams {

  override def name: PluginName = PluginName("transform-request")

  var verticleConf: TransformRequestPluginVerticleConf = _

  override def initService(): Unit = {
    // if PLUGIN_TRANSFORM_REQUEST_CONF_REF in plugin/transform-request.json is not set
    // then verticleConfig.conf gets resolved to empty string "" which cause deserialization issues
    // below Decoder[Option[JsonObject]] treats empty string as None
    implicit val confDecoder: Decoder[Option[JsonObject]] =
    Decoder.decodeOption[JsonObject].or {
      Decoder.decodeString.emap {
        case "" => Right(None)
        case x => Left("could not decode optional JsonObject")
      }
    }
    verticleConf = decodeConfigUnsafe(deriveDecoder[TransformRequestPluginVerticleConf])
  }

  override def apply(ctx: RequestCtx, conf: TransformerConf): Future[RequestCtx] = Future.successful {
    val jsonBodyOpt = parseJsonBodyIfRequired(ctx, conf)
    ctx |>
      transformPathParams(resolvePathParamOps(ctx, conf.pathParams, jsonBodyOpt)) |>
      transformQueryParams(resolveQueryParamOps(ctx, conf.queryParams, jsonBodyOpt)) |>
      transformReqJsonBody(resolveBodyOps(ctx, conf.body, jsonBodyOpt), jsonBodyOpt) |>
      transformReqHeaders(resolveHeaderOps(ctx, conf.headers, jsonBodyOpt))
  }

  // to avoid parsing body to JsonObject over and over again we do it only if JSON body reference exists or body operations are set
  def parseJsonBodyIfRequired(ctx: RequestCtx, conf: TransformerConf): Option[JsonObject] = {
    if (conf.parseJsonBody)
      Try(ctx.targetRequest.bodyOpt.map(_.toJsonObject)) match {
        case Success(jsonBodyOpt) => jsonBodyOpt
        case Failure(ex) =>
          log.error(ctx.tracingCtx, "Could not parse JSON body", ex)
          None
      }
    else None
  }

  private def confValues(): JsonObject =
    verticleConf.conf.getOrElse(new JsonObject())

  def resolveBodyOps(ctx: RequestCtx, bodyOps: BodyOps, jsonBodyOpt: Option[JsonObject]): ResolvedBodyOps =
    ResolvedBodyOps(bodyOps.set.map(_.mapValues(resolveJson(ctx, jsonBodyOpt, confValues(), _))), bodyOps.remove, bodyOps.drop, bodyOps.nullIfAbsent)

  def resolvePathParamOps(ctx: RequestCtx, pathParamOps: PathParamOps, jsonBodyOpt: Option[JsonObject]): ResolvedPathParamOps =
    ResolvedPathParamOps(pathParamOps.set.map(_.mapValues(ValueResolver.resolveString(ctx, jsonBodyOpt, confValues(), _))))

  def resolveQueryParamOps(ctx: RequestCtx, queryParamOps: QueryParamOps, jsonBodyOpt: Option[JsonObject]): ResolvedQueryParamOps =
    ResolvedQueryParamOps(queryParamOps.set.map(_.mapValues(ValueResolver.resolveListOfStrings(ctx, jsonBodyOpt, confValues(), _))))

  def resolveHeaderOps(ctx: RequestCtx, headerOps: HeaderOps, jsonBodyOpt: Option[JsonObject]): ResolvedHeaderOps =
    ResolvedHeaderOps(headerOps.set.map(_.mapValues(ValueResolver.resolveListOfStrings(ctx, jsonBodyOpt, confValues(), _))))

  override def validate(conf: TransformerConf): ValidateResponse = ValidateOk

  override def confDecoder: Decoder[TransformerConf] = TransformerConf.TransformerConfDecoder

  override def convertOpenApi(openApi: Swagger, rule: OpenApiRule, conf: TransformerConf): ConvertOpenApiResponse =
    TransformRequestOpenApiConverter.convertOpenApi(openApi, rule, conf)
}
