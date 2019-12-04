package com.cloudentity.pyron.plugin.impl.transformer

import io.circe.Decoder
import com.cloudentity.pyron.plugin.config._
import com.cloudentity.pyron.domain.flow.{PathParams, PluginName, RequestCtx}
import com.cloudentity.pyron.domain.http.Headers
import com.cloudentity.pyron.domain.openapi.OpenApiRule
import com.cloudentity.pyron.openapi.OpenApiPluginUtils
import com.cloudentity.pyron.plugin.openapi._
import com.cloudentity.pyron.plugin.util.value._
import com.cloudentity.pyron.plugin.verticle.RequestPluginVerticle
import io.swagger.models.Swagger
import io.swagger.models.parameters.{Parameter, PathParameter}
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.{JsonArray, JsonObject}
import io.vertx.core.logging.LoggerFactory

import scala.concurrent.Future
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class TransformRequestPlugin extends RequestPluginVerticle[TransformerConf]
  with TransformJsonBody with TransformPathParams with TransformHeaders {

  override def name: PluginName = PluginName("transform-request")

  override def apply(ctx: RequestCtx, conf: TransformerConf): Future[RequestCtx] = Future.successful {
    val jsonBodyOpt = parseJsonBodyIfRequired(ctx, conf)

    ctx |>
      transformJsonBody(resolveBodyOps(ctx, conf.body, jsonBodyOpt), jsonBodyOpt) |>
      transformPathParams(resolvePathParamOps(ctx, conf.pathParams, jsonBodyOpt)) |>
      transformHeaders(resolveHeaderOps(ctx, conf.headers, jsonBodyOpt))
  }

  // to avoid parsing body to JsonObject over and over again we do it only if JSON body reference exists or body operations are set
  def parseJsonBodyIfRequired(ctx: RequestCtx, conf: TransformerConf): Option[JsonObject] = {
    if (conf.parseJsonBody)
      Try(ctx.request.bodyOpt.map(_.toJsonObject)) match {
        case Success(jsonBodyOpt) => jsonBodyOpt
        case Failure(ex) =>
          log.error(ctx.tracingCtx, "Could not parse JSON body", ex)
          None
      }
    else None
  }

  def resolveBodyOps(ctx: RequestCtx, bodyOps: BodyOps, jsonBodyOpt: Option[JsonObject]): ResolvedBodyOps =
    ResolvedBodyOps(bodyOps.set.map(_.map { case (path, valueOrRef) => path -> ValueResolver.resolveJson(ctx, jsonBodyOpt, valueOrRef)}), bodyOps.drop)

  def resolvePathParamOps(ctx: RequestCtx, pathParamOps: PathParamOps, jsonBodyOpt: Option[JsonObject]): ResolvedPathParamOps =
    ResolvedPathParamOps(pathParamOps.set.map(_.map { case (path, valueOrRef) => path -> ValueResolver.resolveString(ctx, jsonBodyOpt, valueOrRef)}))

  def resolveHeaderOps(ctx: RequestCtx, headerOps: HeaderOps, jsonBodyOpt: Option[JsonObject]): ResolvedHeaderOps =
    ResolvedHeaderOps(headerOps.set.map(_.map { case (path, valueOrRef) => path -> ValueResolver.resolveListOfStrings(ctx, jsonBodyOpt, valueOrRef)}))

  override def validate(conf: TransformerConf): ValidateResponse = ValidateOk

  override def confDecoder: Decoder[TransformerConf] = TransformerConf.TransformerConfDecoder

  override def convertOpenApi(openApi: Swagger, rule: OpenApiRule, conf: TransformerConf): ConvertOpenApiResponse =
    TransformRequestOpenApiConverter.convertOpenApi(openApi, rule, conf)
}

object TransformJsonBody extends TransformJsonBody
trait TransformJsonBody {
  def transformJsonBody(bodyOps: ResolvedBodyOps, jsonBodyOpt: Option[JsonObject])(ctx: RequestCtx): RequestCtx =
    jsonBodyOpt match {
      case Some(jsonBody) =>
        val transformedBody = applyBodyTransformations(bodyOps, jsonBody.copy())
        ctx.modifyRequest(_.copy(bodyOpt = Some(transformedBody)))
      case None =>
        ctx
    }

  // NOTE: this method mutates jsonBody
  // apply here other body transformations
  def applyBodyTransformations(bodyOps: ResolvedBodyOps, jsonBody: JsonObject): Buffer =
    if (bodyOps.drop.getOrElse(false)) Buffer.buffer()
    else setJsonBody(bodyOps.set.getOrElse(Map()))(jsonBody).toBuffer

  // NOTE: this method mutates jsonBody
  def setJsonBody(set: Map[Path, Option[JsonValue]])(body: JsonObject): JsonObject = {
    def mutateBodyAttribute(body: JsonObject, bodyPath: List[String], resolvedValue: Option[JsonValue]): Unit =
      bodyPath match {
        case key :: Nil =>
          body.put(key, resolvedValue.map(_.rawValue).getOrElse(null))

        case key :: tail =>
          val leaf =
            Try(Option(body.getJsonObject(key))) match {
              case Success(Some(obj)) => obj
              case _ => // in case of Failure we overwrite a value that is not JsonObject
                val obj = new JsonObject()
                body.put(key, obj)
                obj
            }

          mutateBodyAttribute(leaf, tail, resolvedValue)
        case Nil => ()
      }

    set.foldLeft(body) { case (b, (bodyPath, value)) =>
      mutateBodyAttribute(b, bodyPath.value, value)
      b
    }
  }
}

object TransformPathParams extends TransformPathParams
trait TransformPathParams {
  def transformPathParams(pathParamsOps: ResolvedPathParamOps)(ctx: RequestCtx): RequestCtx = {
    val transformedPathParams = applyPathParamsTransformations(pathParamsOps)(ctx.request.uri.pathParams)
    ctx.modifyRequest(_.modifyPathParams(_ => transformedPathParams))
  }

  // apply here other path-param transformations
  def applyPathParamsTransformations(pathParamOps: ResolvedPathParamOps)(pathParams: PathParams): PathParams =
    setPathParams(pathParamOps.set.getOrElse(Map()))(pathParams)

  def setPathParams(set: Map[String, Option[String]])(pathParams: PathParams): PathParams =
    set.foldLeft(pathParams) { case (params, (paramName, valueOpt)) =>
      valueOpt match {
        case Some(value) => PathParams(params.value.updated(paramName, value))
        case None => PathParams(params.value - paramName)
      }
    }
}

object TransformHeaders extends TransformHeaders
trait TransformHeaders {
  def transformHeaders(headerOps: ResolvedHeaderOps)(ctx: RequestCtx): RequestCtx = {
    val transformedHeaders = applyHeadersTransformations(headerOps)(ctx.request.headers)
    ctx.modifyRequest(_.modifyHeaders(_ => transformedHeaders))
  }

  // apply here other header transformations
  def applyHeadersTransformations(headerOps: ResolvedHeaderOps)(headers: Headers): Headers =
    setHeaders(headerOps.set.getOrElse(Map()))(headers)

  def setHeaders(set: Map[String, Option[List[String]]])(headers: Headers): Headers =
    set.foldLeft(headers) { case (hs, (headerName, valueOpt)) =>
      valueOpt match {
        case Some(value) => hs.setValues(headerName, value)
        case None => hs.remove(headerName)
      }
    }
}

object TransformRequestOpenApiConverter extends OpenApiPluginUtils {
  val log = LoggerFactory.getLogger(this.getClass)

  def convertOpenApi(swagger: Swagger, rule: OpenApiRule, conf: TransformerConf): ConvertOpenApiResponse = {
    convertPathParams(conf.pathParams)(rule)(swagger)
  }

  private def convertPathParams(pathParams: PathParamOps)(rule: OpenApiRule)(swagger: Swagger) =
    pathParams.set match {
      case Some(set) =>
        findOperation(swagger, rule) match {
          case Some(swaggerOperation) =>
            val pathParamsToTransform: List[(Parameter, ValueOrRef)] =
              swaggerOperation.getParameters.asScala
                .filter(_.isInstanceOf[PathParameter])
                .flatMap { swaggerParam =>
                  set.find { case (pathParamToTransform, _) => pathParamToTransform == swaggerParam.getName } // TODO what if path-param names in Rule mismatch Swagger but are the same thing?
                    .map { case (_, valueOrRef) => (swaggerParam, valueOrRef) }
                }.toList

            pathParamsToTransform.foreach { case (swaggerParam, valueOrRef) =>
              valueOrRef match {
                case Value(_)        => swaggerOperation.getParameters.remove(swaggerParam)
                case AuthnRef(_)     => swaggerOperation.getParameters.remove(swaggerParam)
                case BodyRef(_)      => // TODO implement
                case PathParamRef(_) => // TODO implement
                case HeaderRef(_, _) => // TODO implement
              }
            }
          case None =>
            log.warn(s"Can't find rule definition: $rule in target service docs", "")
          // operation not found - do nothing
        }
        ConvertedOpenApi(swagger)

      case None => ConvertedOpenApi(swagger)
    }
}