package com.cloudentity.pyron.rule

import com.cloudentity.pyron.domain.flow.{ApiGroupPluginConf, RequestCtx, ResponseCtx}
import com.cloudentity.pyron.domain.rule.RuleConfWithPlugins
import com.cloudentity.pyron.plugin.PluginFunctions.{RequestPlugin, ResponsePlugin}
import com.cloudentity.pyron.plugin.bus.{request, response}
import com.cloudentity.pyron.plugin.config._
import com.cloudentity.pyron.plugin.{RequestPluginService, ResponsePluginService, ValidatePluginService}
import com.cloudentity.tools.vertx.bus.VertxEndpointClient
import com.cloudentity.tools.vertx.scala.{Futures, VertxExecutionContext}
import com.cloudentity.tools.vertx.tracing.TracingManager
import io.vertx.core.Vertx
import scalaz.Scalaz._
import scalaz._

import java.util
import scala.concurrent.Future

object RuleBuilder {
  case class InvalidPluginConf(conf: ApiGroupPluginConf, errorMsg: String)

  /**
    * Validates plugins configuration for all rules and aggregates errors if any.
    * If there is no errors Rule with configured plugin clients is returned.
    */
  def build(vertx: Vertx, tracing: TracingManager, conf: RuleConfWithPlugins)(implicit ec: VertxExecutionContext): Future[ValidationNel[InvalidPluginConf, Rule]] =
    for {
      requestVal  <- validatePluginConfigs(vertx, conf.requestPlugins.toList)
      responseVal <- validatePluginConfigs(vertx, conf.responsePlugins.toList)
    } yield (requestVal |@| responseVal).apply((_, _) => buildRule(vertx, tracing, conf))

  def buildRule(vertx: Vertx, tracing: TracingManager, conf: RuleConfWithPlugins)(implicit ec: VertxExecutionContext): Rule =
    Rule(
      conf.rule,
      buildRequestFunctions(vertx, tracing, conf.requestPlugins.toList),
      buildResponseFunctions(vertx, tracing, conf.requestPlugins.toList.reverse ::: conf.responsePlugins.toList)
    )

  def buildRequestFunctions(vertx: Vertx, tracing: TracingManager, confs: List[ApiGroupPluginConf])(implicit ec: VertxExecutionContext): List[RequestPlugin] =
    confs.map { conf =>
      val client = VertxEndpointClient.makeWithTracing(vertx, tracing, classOf[RequestPluginService], getAddressPrefix(conf))
      (ctx: RequestCtx) =>
        Futures.toScala(client.applyPlugin(ctx.tracingCtx, request.ApplyRequest(ctx, conf))).flatMap {
          case request.Continue(nextCtx) => Future.successful(nextCtx)
          case request.ApplyError(ex)    => Future.failed(ex)
        }
    }

  def buildResponseFunctions(vertx: Vertx, tracing: TracingManager, confs: List[ApiGroupPluginConf])(implicit ec: VertxExecutionContext): List[ResponsePlugin] =
    confs.map { conf =>
      val client = VertxEndpointClient.makeWithTracing(vertx, tracing, classOf[ResponsePluginService], getAddressPrefix(conf))
      (ctx: ResponseCtx) =>
        Futures.toScala(client.applyPlugin(ctx.tracingCtx, response.ApplyRequest(ctx, conf))).flatMap {
          case response.Continue(apiResponse) => Future.successful(apiResponse)
          case response.ApplyError(ex)        => Future.failed(ex)
        }
    }

  def validatePluginConfigs(vertx: Vertx, confs: List[ApiGroupPluginConf])(implicit ec: VertxExecutionContext): Future[ValidationNel[InvalidPluginConf, Unit]] = {
    val validationsFut: Future[List[Validation[NonEmptyList[InvalidPluginConf], Unit]]] =
      Future.sequence {
        confs.map { conf =>
          val client = VertxEndpointClient.make(vertx, classOf[ValidatePluginService], getAddressPrefix(conf))

          Futures.toScala(client.validateConfig(ValidateRequest(conf)))
            .map[ValidationNel[InvalidPluginConf, Unit]] {
              case ValidateOk           => Success(())
              case ValidateFailure(msg) => Failure(NonEmptyList(InvalidPluginConf(conf, msg)))
              case ValidateError(msg)   => Failure(NonEmptyList(InvalidPluginConf(conf, msg)))
            }
        }
      }

    validationsFut.map { validations =>
      // aggregate errors from all invalid plugin configurations
      validations.foldLeft[ValidationNel[InvalidPluginConf, Unit]](Success(())) { case (acc, validation) =>
        (acc |@| validation).apply((_, _) => ())
      }
    }
  }

  def getAddressPrefix(conf: ApiGroupPluginConf): util.Optional[String] =
    java.util.Optional.of(conf.addressPrefixOpt.map(_.value).getOrElse(conf.name.value))

}
