package com.cloudentity.pyron.plugin.verticle

import com.cloudentity.pyron.domain.flow.PluginName
import io.circe.{CursorOp, Decoder, Json}
import com.cloudentity.pyron.plugin.config._
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.impl.NoStackTraceThrowable

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait PluginConfValidator[C] {
  def name: PluginName
  def log: LoggingWithTracing
  def validate(conf: C): ValidateResponse
  def confDecoder: Decoder[C]

  def decodeRuleConf(rawRuleConf: Json): Either[Throwable, C] =
    confDecoder.decodeJson(rawRuleConf)
      .left.map { failure =>
        new NoStackTraceThrowable(s"Could not decode '${name.value}' plugin rule configuration '${rawRuleConf.noSpaces}': invalid 'conf${CursorOp.opsToPath(failure.history)}'")
      }

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
          ValidateError(err.getMessage)
      }
    }
}