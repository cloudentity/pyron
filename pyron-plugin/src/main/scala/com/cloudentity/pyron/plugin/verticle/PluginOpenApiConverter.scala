package com.cloudentity.pyron.plugin.verticle

import io.circe.Decoder
import com.cloudentity.pyron.domain.openapi.OpenApiRule
import com.cloudentity.pyron.plugin.openapi._
import com.cloudentity.pyron.plugin.openapi._
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.swagger.models.Swagger

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait PluginOpenApiConverter[C] {
  def log: LoggingWithTracing
  def convertOpenApi(openApi: Swagger, rule: OpenApiRule, conf: C): ConvertOpenApiResponse = ConvertedOpenApi(openApi)
  def confDecoder: Decoder[C]

  def handleConvertOpenApi(req: ConvertOpenApiRequest): Future[ConvertOpenApiResponse] =
    Future.successful {
      confDecoder.decodeJson(req.conf.conf) match {
        case Right(conf) =>
          Try(convertOpenApi(req.swagger, req.rule, conf)) match {
            case Success(result) => result
            case Failure(ex) =>
              log.error(TracingContext.dummy(), s"Error on converting OpenApi docs: conf=$conf, rule=${req.rule}", ex)
              ConvertOpenApiError(ex.getMessage)
          }
        case Left(err) =>
          log.error(TracingContext.dummy(), s"Could not decode plugin configuration: ${req.conf}", err)
          ConvertOpenApiError(err.message)
      }
    }
}