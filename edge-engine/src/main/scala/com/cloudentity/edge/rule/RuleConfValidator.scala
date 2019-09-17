package com.cloudentity.edge.rule

import com.cloudentity.edge.domain.rule.RuleConfWithPlugins
import com.cloudentity.edge.rule.RuleBuilder.InvalidPluginConf
import com.cloudentity.tools.vertx.tracing.TracingManager
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import io.vertx.core.Vertx

import scala.concurrent.Future
import scalaz.{-\/, Failure, NonEmptyList, Success, ValidationNel, \/, \/-}

object RuleConfValidator {
  type PluginConfValidationError = (RuleConfWithPlugins, NonEmptyList[InvalidPluginConf])
  def validatePluginConfsAndBuildRules(vertx: Vertx, tracing: TracingManager, ruleConfs: List[RuleConfWithPlugins])
                                      (implicit ec: VertxExecutionContext): Future[List[PluginConfValidationError] \/ List[Rule]] = {
    val sequence: Future[List[(RuleConfWithPlugins, ValidationNel[InvalidPluginConf, Rule])]] =
      buildRules(vertx, tracing, ruleConfs)

    sequence.map { validations =>
      val rules    = validations.collect { case (conf, Success(rule)) => rule }
      val failures = validations.collect { case (conf, Failure(errors)) => (conf, errors) }

      if (failures.isEmpty) \/-(rules)
      else                  -\/(failures)
    }
  }

  def buildRules(vertx: Vertx, tracing: TracingManager,ruleConfs: List[RuleConfWithPlugins])
                (implicit ec: VertxExecutionContext): Future[List[(RuleConfWithPlugins, ValidationNel[InvalidPluginConf, Rule])]] =
    Future.sequence {
      ruleConfs.map { conf =>
        RuleBuilder.build(vertx, tracing, conf).map((conf, _))
      }
    }
}
