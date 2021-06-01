package com.cloudentity.pyron.plugin.impl.transformer

import com.cloudentity.pyron.domain.flow.{PathParams, PluginName, RequestCtx}
import com.cloudentity.pyron.domain.http.{Headers, QueryParams}
import com.cloudentity.pyron.domain.openapi.OpenApiRule
import com.cloudentity.pyron.openapi.OpenApiPluginUtils
import com.cloudentity.pyron.plugin.config._
import com.cloudentity.pyron.plugin.openapi._
import com.cloudentity.pyron.plugin.util.value._
import com.cloudentity.pyron.plugin.verticle.RequestPluginVerticle
import io.circe.Decoder
import io.circe.generic.semiauto._
import io.swagger.models.Swagger
import io.swagger.models.parameters.{Parameter, PathParameter}
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.{Logger, LoggerFactory}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class TransformRequestPluginVerticleConf(conf: Option[JsonObject])

class TransformRequestPlugin extends RequestPluginVerticle[TransformerConf]
  with TransformJsonBody with TransformPathParams with TransformQueryParams with TransformHeaders {

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
      transformJsonBody(resolveBodyOps(ctx, conf.body, jsonBodyOpt), jsonBodyOpt) |>
      transformPathParams(resolvePathParamOps(ctx, conf.pathParams, jsonBodyOpt)) |>
      transformQueryParams(resolveQueryParamOps(ctx, conf.queryParams, jsonBodyOpt)) |>
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

  private def confValues(): JsonObject =
    verticleConf.conf.getOrElse(new JsonObject())

  def resolveBodyOps(ctx: RequestCtx, bodyOps: BodyOps, jsonBodyOpt: Option[JsonObject]): ResolvedBodyOps =
    ResolvedBodyOps(bodyOps.set.map(_.mapValues(ValueResolver.resolveJson(ctx, jsonBodyOpt, confValues(), _))), bodyOps.drop)

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

  def applyBodyTransformations(bodyOps: ResolvedBodyOps, jsonBody: JsonObject): Buffer =
    if (bodyOps.drop.getOrElse(false)) Buffer.buffer()
    else setJsonBody(bodyOps.set.getOrElse(Map()))(jsonBody).toBuffer

  def setJsonBody(set: Map[Path, Option[JsonValue]])(body: JsonObject): JsonObject = {
    @tailrec
    def mutateBodyAttribute(body: JsonObject, bodyPath: List[String], resolvedValue: Option[JsonValue]): Unit =
      bodyPath match {
        case key :: Nil =>
          body.put(key, resolvedValue.map(_.rawValue).orNull)

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

  def applyPathParamsTransformations(pathParamOps: ResolvedPathParamOps)(pathParams: PathParams): PathParams =
    setPathParams(pathParamOps.set.getOrElse(Map()))(pathParams)

  def setPathParams(set: Map[String, Option[String]])(pathParams: PathParams): PathParams = {
    set.foldLeft(pathParams) { case (params, (paramName, valueOpt)) =>
      valueOpt match {
        case Some(value) => PathParams(params.value.updated(paramName, value))
        case None => PathParams(params.value - paramName)
      }
    }
  }
}

object TransformQueryParams extends TransformQueryParams
trait TransformQueryParams {
  def transformQueryParams(queryParamsOps: ResolvedQueryParamOps)(ctx: RequestCtx): RequestCtx = {
    val transformedQueryParams = applyQueryParamsTransformations(queryParamsOps)(ctx.request.uri.query)
    ctx.modifyRequest(_.modifyQueryParams(_ => transformedQueryParams))
  }

  def applyQueryParamsTransformations(queryParamOps: ResolvedQueryParamOps)(queryParams: QueryParams): QueryParams =
    setQueryParams(queryParamOps.set.getOrElse(Map()))(queryParams)

  def setQueryParams(set: Map[String, Option[List[String]]])(queryParams: QueryParams): QueryParams = {
    set.foldLeft(queryParams) { case (ps, (paramName, valueOpt)) =>
      valueOpt match {
        case Some(value) => ps.setValues(paramName, value)
        case None => ps.remove(paramName)
      }
    }
  }
}

object TransformHeaders extends TransformHeaders
trait TransformHeaders {
  def transformHeaders(headerOps: ResolvedHeaderOps)(ctx: RequestCtx): RequestCtx = {
    val transformedHeaders = applyHeadersTransformations(headerOps)(ctx.request.headers)
    ctx.modifyRequest(_.modifyHeaders(_ => transformedHeaders))
  }

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
  val log: Logger = LoggerFactory.getLogger(this.getClass)

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
                case Value(_)         => swaggerOperation.getParameters.remove(swaggerParam)
                case AuthnRef(_)      => swaggerOperation.getParameters.remove(swaggerParam)
                case BodyRef(_)       => // TODO implement
                case PathParamRef(_)  => // TODO implement
                case QueryParamRef(_) => // TODO implement
                case CookieRef(_)     => // TODO implement
                case HeaderRef(_, _)  => // TODO implement
                case _ =>                // TODO implement
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
