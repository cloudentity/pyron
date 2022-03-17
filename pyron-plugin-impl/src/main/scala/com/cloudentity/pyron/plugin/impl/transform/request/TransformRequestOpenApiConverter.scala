package com.cloudentity.pyron.plugin.impl.transform.request

import com.cloudentity.pyron.domain.openapi.OpenApiRule
import com.cloudentity.pyron.openapi.OpenApiPluginUtils
import com.cloudentity.pyron.plugin.impl.transform.{PathParamOps, RequestTransformerConf}
import com.cloudentity.pyron.plugin.openapi.{ConvertOpenApiResponse, ConvertedOpenApi}
import com.cloudentity.pyron.plugin.util.value._
import io.swagger.models.Swagger
import io.swagger.models.parameters.{Parameter, PathParameter}
import io.vertx.core.logging.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
object TransformRequestOpenApiConverter extends OpenApiPluginUtils {
  val log: Logger = LoggerFactory.getLogger(this.getClass)

  def convertOpenApi(swagger: Swagger, rule: OpenApiRule, conf: RequestTransformerConf): ConvertOpenApiResponse = {
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
                case Value(_) => swaggerOperation.getParameters.remove(swaggerParam)
                case AuthnRef(_) => swaggerOperation.getParameters.remove(swaggerParam)
                case RequestBodyRef(_) => // TODO implement
                case ResponseBodyRef(_) => // TODO implement
                case PathParamRef(_) => // TODO implement
                case QueryParamRef(_) => // TODO implement
                case CookieRef(_) => // TODO implement
                case RequestHeaderRef(_, _) => // TODO implement
                case ResponseHeaderRef(_, _) => // TODO implement
                case _ => // TODO implement
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
