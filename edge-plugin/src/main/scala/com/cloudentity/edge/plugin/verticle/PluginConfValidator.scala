package com.cloudentity.edge.plugin.verticle

import io.circe.{Decoder, Json}
import com.cloudentity.edge.plugin.config._
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait PluginConfValidator[C] {
  def log: LoggingWithTracing
  def validate(conf: C): ValidateResponse
  def confDecoder: Decoder[C]

  def decodeRuleConf(rawRuleConf: Json): Decoder.Result[C] =
    confDecoder.decodeJson(rawRuleConf)

  def handleValidate(req: ValidateRequest): Future[ValidateResponse] =
    Future.successful {
      decodeRuleConf(req.conf.conf) match {
        case Right(conf) =>
          Try(validate(conf)) match {
            case Success(result) => result
            case Failure(ex) =>
              log.error(TracingContext.dummy(), s"Error on validating plugin configuration: $conf", ex)
              ValidateError(ex.getMessage)
          }
        case Left(err) =>
          log.error(TracingContext.dummy(), s"Could not decode plugin configuration: ${req.conf}", err)
          ValidateError(err.message)
      }
    }
}