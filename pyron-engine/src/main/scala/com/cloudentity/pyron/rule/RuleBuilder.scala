package com.cloudentity.pyron.rule

import com.cloudentity.pyron.plugin.config._
import com.cloudentity.pyron.domain._
import com.cloudentity.pyron.domain.flow.{PluginConf, ApiGroupPluginConf, PluginName, RequestCtx, ResponseCtx}
import com.cloudentity.pyron.domain.rule.RuleConfWithPlugins
import com.cloudentity.pyron.plugin.PluginFunctions.{RequestPlugin, ResponsePlugin}
import com.cloudentity.pyron.plugin.bus.{request, response}
import com.cloudentity.pyron.plugin.config._
import com.cloudentity.pyron.plugin.{RequestPluginService, ResponsePluginService, ValidatePluginService}
import com.cloudentity.tools.vertx.bus.ServiceClientFactory
import com.cloudentity.tools.vertx.scala.Futures
import com.cloudentity.tools.vertx.tracing.TracingManager
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import io.vertx.core.Vertx
import io.vertx.core.eventbus.EventBus

import scala.concurrent.Future
import scalaz.Scalaz._
import scalaz._

object RuleBuilder {
  case class InvalidPluginConf(conf: ApiGroupPluginConf, errorMsg: String)

  /**
    * Validates plugins configuration for all rules and aggregates errors if any.
    * If there is no errors Rule with configured plugin clients is returned.
    */
  def build(vertx: Vertx, tracing: TracingManager, conf: RuleConfWithPlugins)(implicit ec: VertxExecutionContext): Future[ValidationNel[InvalidPluginConf, Rule]] =
    for {
      requestVal  <- validatePluginConfigs(vertx.eventBus(), conf.requestPlugins.toList)
      responseVal <- validatePluginConfigs(vertx.eventBus(), conf.responsePlugins.toList)
    } yield (requestVal |@| responseVal).apply((_, _) => buildRule(vertx.eventBus(), tracing, conf))

  def buildRule(bus: EventBus, tracing: TracingManager, conf: RuleConfWithPlugins)(implicit ec: VertxExecutionContext): Rule =
    Rule(
      conf.rule,
      buildRequestFunctions(bus, tracing, conf.requestPlugins.toList),
      buildResponseFunctions(bus, tracing, conf.requestPlugins.toList.reverse ::: conf.responsePlugins.toList)
    )

  def buildRequestFunctions(bus: EventBus, tracing: TracingManager, confs: List[ApiGroupPluginConf])(implicit ec: VertxExecutionContext): List[RequestPlugin] =
    confs.map { conf =>

      val client = ServiceClientFactory.makeWithTracing(bus, tracing, classOf[RequestPluginService], java.util.Optional.of(conf.addressPrefixOpt.map(_.value).getOrElse(conf.name.value)))
      (ctx: RequestCtx) =>
        Futures.toScala(client.applyPlugin(ctx.tracingCtx, request.ApplyRequest(ctx, conf))).flatMap {
          case request.Continue(nextCtx) => Future.successful(nextCtx)
          case request.ApplyError(ex)    => Future.failed(ex)
        }
    }

  def buildResponseFunctions(bus: EventBus, tracing: TracingManager, confs: List[ApiGroupPluginConf])(implicit ec: VertxExecutionContext): List[ResponsePlugin] =
    confs.map { conf =>

      val client = ServiceClientFactory.makeWithTracing(bus, tracing, classOf[ResponsePluginService], java.util.Optional.of(conf.addressPrefixOpt.map(_.value).getOrElse(conf.name.value)))
      (ctx: ResponseCtx) =>
        Futures.toScala(client.applyPlugin(ctx.tracingCtx, response.ApplyRequest(ctx, conf))).flatMap {
          case response.Continue(apiResponse) => Future.successful(apiResponse)
          case response.ApplyError(ex)        => Future.failed(ex)
        }
    }

  def validatePluginConfigs(bus: EventBus, confs: List[ApiGroupPluginConf])(implicit ec: VertxExecutionContext): Future[ValidationNel[InvalidPluginConf, Unit]] = {
    val validationsFut: Future[List[Validation[NonEmptyList[InvalidPluginConf], Unit]]] =
      Future.sequence {
        confs.map { conf =>
          val client = ServiceClientFactory.make(bus, classOf[ValidatePluginService], java.util.Optional.of(conf.addressPrefixOpt.map(_.value).getOrElse(conf.name.value)))

          val validationResponses: Future[(ValidateResponse, ValidateResponse)] =
            for {
              validateConfig <- Futures.toScala(client.validateConfig(ValidateRequest(conf)))
              validateIf     <- Futures.toScala(client.validateApplyIf(ValidateRequest(conf)))
            } yield (validateConfig, validateIf)

          validationResponses.map[ValidationNel[InvalidPluginConf, Unit]] { case (validateConfig, validateIf) =>
            val errors = List(validateConfig.koMsg, validateIf.koMsg).flatten
            errors match {
              case Nil => Success(())
              case head :: tail => Failure(NonEmptyList(InvalidPluginConf(conf, head), tail.map(el => InvalidPluginConf(conf, el)):_*))
            }
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
}
