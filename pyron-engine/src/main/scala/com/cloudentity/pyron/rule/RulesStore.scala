package com.cloudentity.pyron.rule

import com.cloudentity.pyron.domain.Codecs._
import com.cloudentity.pyron.domain.flow.{PluginConf, PluginName}
import com.cloudentity.pyron.domain.rule.RuleConfWithPlugins
import com.cloudentity.pyron.plugin.{ExtendRules, PluginRulesExtendService, PluginService}
import com.cloudentity.pyron.rule.RuleBuilder.InvalidPluginConf
import com.cloudentity.pyron.rule.RuleConfValidator.PluginConfValidationError
import com.cloudentity.pyron.rule.RulesConfReader.{RuleDecodingError, RuleErrors}
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Json, Printer}
import io.vertx.core.{Future => VxFuture}
import org.slf4j.LoggerFactory
import scalaz.{-\/, \/-}

import scala.concurrent.Future

trait RulesStore {
  @VertxEndpoint
  def decodeRules(tag: String, rules: Json, proxyRulesOpt: Option[Json]): VxFuture[(List[Rule], List[RuleConfWithPlugins])]

  @VertxEndpoint
  def decodeRuleConfsWithPlugins(tag: String, rules: Json, proxyRulesOpt: Option[Json]): VxFuture[List[RuleConfWithPlugins]]
}

case class RulesChanged(rules: List[Rule], confs: List[RuleConfWithPlugins])

case class RulesDecodingSuccess(rules: List[Rule], confs: List[RuleConfWithPlugins])
case class RulesDecodingError(errors: List[PluginConfValidationError])

class RulesStoreVerticle extends ScalaServiceVerticle with RulesStore {
  val log = LoggerFactory.getLogger(this.getClass)

  def decodeRules(tag: String, rules: Json, proxyRulesOpt: Option[Json]): VxFuture[(List[Rule], List[RuleConfWithPlugins])] =
    decodeRuleConfsWithPlugins(tag, rules, proxyRulesOpt).toScala.flatMap(confs => buildRules(confs).map((_, confs))).toJava

  def decodeRuleConfsWithPlugins(tag: String, rules: Json, proxyRulesOpt: Option[Json]): VxFuture[List[RuleConfWithPlugins]] = {
    for {
      rules <- readRulesConf(rules, tag)
      proxyRules <- proxyRulesOpt.map(readRulesConf(_, tag + "_proxy")).getOrElse(Future.successful(Nil))
    } yield (rules ::: proxyRules)
    }.toJava

  private def readRulesConf(rulesConf: Json, tag: String): Future[List[RuleConfWithPlugins]] =
    RulesConfReader.read(rulesConf.toString) match {
      case \/-(rules) =>
        log.info(s"Rules (${tag}) configuration:\n${rules.map(rule => s"   ${rule.asJson.pretty(Printer.noSpaces.copy(dropNullValues = true))}").mkString("\n")}\n")
        for {
          _             <- checkPluginsReady(rules)
          extendedRules <- Future.sequence(rules.map(extendedRuleConfs)).map(_.flatten).map { extendedRules =>
            if (extendedRules != rules) {
              log.info(s"Rules (${tag}, extended) configuration:\n${extendedRules.map(rule => s"   ${rule.asJson.noSpaces}").mkString("\n")}")
            }
            extendedRules
          }
        } yield extendedRules
      case -\/(RuleDecodingError(ex)) => Future.failed(new Exception(ex))
      case -\/(RuleErrors(errors)) => Future.failed(throw new Exception(s"Could not read Rule (${tag}) configurations:\n" + errors.list.toList.mkString("\n")))
    }

  private def checkPluginsReady(rules: List[RuleConfWithPlugins]): Future[Unit] = {
    val results: Future[Set[Either[PluginName, Unit]]] =
      Future.sequence {
        val pluginNames: Set[PluginName] =
          rules.flatMap(r => r.requestPlugins.toList.map(_.name) ::: r.responsePlugins.toList.map(_.name)).toSet

        pluginNames.map { pluginName =>
          createClient(classOf[PluginService], pluginName.value).isReady.toScala()
            .flatMap { ready =>
              if (ready) Future.successful(Right(()))
              else Future.successful(Left(pluginName))
            }.recover { case _ => Left(pluginName) }
        }
      }

    results.flatMap { results =>
      val errors = results.collect { case Left(pluginName) => pluginName }

      if (errors.isEmpty) Future.successful(())
      else Future.failed(new Exception(s"Following plugins are used in rules, but are not deployed: [${errors.map(_.value).mkString(", ")}]"))
    }
  }

  def extendedRuleConfs(ruleConf: RuleConfWithPlugins): Future[List[RuleConfWithPlugins]] = {
    val pluginConfs = ruleConf.requestPlugins.toList ::: ruleConf.responsePlugins.toList
    Future.sequence(pluginConfs.map(getExtendRules(ruleConf))).map { ruleMods =>
      val prepend    = ruleMods.flatMap(_.prepend)
      val append     = ruleMods.flatMap(_.append)
      prepend ::: List(ruleConf) ::: append
    }
  }

  def getExtendRules(ruleConf: RuleConfWithPlugins)(pluginConf: PluginConf): Future[ExtendRules] = {
    val client = createClient(classOf[PluginRulesExtendService], java.util.Optional.of(pluginConf.name.value))
    client.extendRules(ruleConf, pluginConf.conf).toScala()
  }

  private def buildRules(ruleConfs: List[RuleConfWithPlugins]): Future[List[Rule]] =
    RuleConfValidator.validatePluginConfsAndBuildRules(vertx, getTracing, ruleConfs).flatMap {
      case \/-(rules) =>
        Future.successful(rules)
      case -\/(errors) =>
        val printer = Printer.noSpaces.copy(dropNullValues = true)
        val validationFailureMsgs = errors.map { case (r, e) =>
          val invalidConfs = e.stream.foldLeft(List[InvalidPluginConf]()) { case (l, el) => el :: l }

          s"""
             |  {
             |    "ruleConf": ${r.asJson.pretty(printer)},
             |    "errors": ${invalidConfs.map("       " + _.asJson.noSpaces).mkString("[\n", "\n", "\n    ]")}
             |  }
             |""".stripMargin
        }
        Future.failed(new Exception(s"Failure on plugin config validations: ${validationFailureMsgs.mkString("[", "\n  ", "]")}"))
    }
}