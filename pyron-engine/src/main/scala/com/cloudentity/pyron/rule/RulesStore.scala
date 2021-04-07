package com.cloudentity.pyron.rule

import com.cloudentity.pyron.domain.Codecs._
import com.cloudentity.pyron.domain.flow.{ApiGroupPluginConf, PluginAddressPrefix, PluginName}
import com.cloudentity.pyron.domain.rule.{RuleConf, RuleConfWithPlugins}
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
import org.slf4j.{Logger, LoggerFactory}
import scalaz.{-\/, \/-}

import scala.concurrent.Future

trait RulesStore {

  @VertxEndpoint
  def decodeRules(tag: String,
                  rules: Json,
                  proxyRulesOpt: Option[Json],
                  addresses: Map[PluginName, PluginAddressPrefix]
                 ): VxFuture[(List[Rule], List[RuleConfWithPlugins])]

  @VertxEndpoint
  def decodeRuleConfsWithPlugins(tag: String,
                                 rules: Json,
                                 proxyRulesOpt: Option[Json],
                                 addresses: Map[PluginName, PluginAddressPrefix]
                                ): VxFuture[List[RuleConfWithPlugins]]

}

case class RulesChanged(rules: List[Rule], confs: List[RuleConfWithPlugins])

case class RulesDecodingSuccess(rules: List[Rule], confs: List[RuleConfWithPlugins])
case class RulesDecodingError(errors: List[PluginConfValidationError])

case class RuleConfWithPluginNames(rule: RuleConf, requestPlugins: List[PluginName], responsePlugins: List[PluginName])

class RulesStoreVerticle extends ScalaServiceVerticle with RulesStore {
  val log: Logger = LoggerFactory.getLogger(this.getClass)

  def decodeRules(tag: String,
                  rules: Json,
                  proxyRulesOpt: Option[Json],
                  addresses: Map[PluginName, PluginAddressPrefix]
                 ): VxFuture[(List[Rule], List[RuleConfWithPlugins])] = {
    decodeRuleConfsWithPlugins(tag, rules, proxyRulesOpt, addresses)
      .toScala.flatMap(confs => buildRules(confs).map((_, confs)))
      .toJava()
  }

  def decodeRuleConfsWithPlugins(tag: String,
                                 rules: Json,
                                 proxyRulesOpt: Option[Json],
                                 addresses: Map[PluginName, PluginAddressPrefix]
                                ): VxFuture[List[RuleConfWithPlugins]] = {
    val result = for {
      rules <- readRulesConf(rules, tag, addresses)
      proxyRules <- proxyRulesOpt.map(readRulesConf(_, tag + "_proxy", addresses)).getOrElse(Future.successful(Nil))
    } yield rules ::: proxyRules
    result.toJava()
  }

  private def readRulesConf(rulesConf: Json, tag: String, addresses: Map[PluginName, PluginAddressPrefix]): Future[List[RuleConfWithPlugins]] =
    RulesConfReader.read(rulesConf.toString, addresses) match {
      case \/-(rules) =>
        logRulesConf(s"Rules ($tag) configuration", rules)
        //log.info(s"Rules (${tag}) configuration:\n${rules.map(rule => s"   ${rule.asJson.pretty(Printer.noSpaces.copy(dropNullValues = true))}").mkString("\n")}\n")
        for {
          _             <- checkPluginsReady(rules)
          extendedRules <- Future.sequence(rules.map(extendedRuleConfs)).map(_.flatten).map { extendedRules =>
            if (extendedRules != rules) {
              logRulesConf(s"Rules ($tag, extended) configuration", rules)
              //log.info(s"Rules (${tag}, extended) configuration:\n${extendedRules.map(rule => s"   ${rule.asJson.noSpaces}").mkString("\n")}")
            }
            extendedRules
          }
        } yield extendedRules
      case -\/(RuleDecodingError(ex)) => Future.failed(new Exception(ex))
      case -\/(RuleErrors(errors)) =>
        val errorsStr = errors.list.toList.mkString("\n")
        Future.failed(throw new Exception(s"Could not read Rule ($tag) configurations:\n" + errorsStr))
    }

  private def logRulesConf(msg: String, rules: List[RuleConfWithPlugins]): Unit = {
    val rs = rules.map { rule =>
      val requestPluginNames = rule.requestPlugins.toList.map(_.name)
      val responsePluginNames = rule.responsePlugins.toList.map(_.name)
      RuleConfWithPluginNames(rule.rule, requestPluginNames, responsePluginNames)
    }

    def getRuleStr(rule: RuleConfWithPluginNames): String =
      s"   ${rule.asJson.pretty(Printer.noSpaces.copy(dropNullValues = true))}"

    log.info(s"$msg:\n${rs.map(getRuleStr).mkString("\n")}\n")
  }

  private def checkPluginsReady(rules: List[RuleConfWithPlugins]): Future[Unit] = {
    val results: Future[Set[Either[PluginName, Unit]]] = Future.sequence {
        val pluginNames: Set[(PluginName, Option[PluginAddressPrefix])] = rules.flatMap(r =>
          r.requestPlugins.toList.map(p => (p.name, p.addressPrefixOpt)) :::
            r.responsePlugins.toList.map(p => (p.name, p.addressPrefixOpt))
        ).toSet

        pluginNames.map { case (pluginName, pluginIdOpt) =>
          createClient(classOf[PluginService], pluginIdOpt.map(_.value)
            .getOrElse(pluginName.value)).isReady().toScala()
            .flatMap { ready =>
              if (ready) Future.successful(Right(()))
              else Future.successful(Left(pluginName))
            }.recover { case _ => Left(pluginName) }
        }
      }

    results.flatMap { results =>
      val errors = results.collect { case Left(pluginName) => pluginName.value }
      if (errors.isEmpty) Future.successful(())
      else Future.failed(new Exception(s"Following plugins are used in rules, but are not deployed: [${errors.mkString(", ")}]"))
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

  def getExtendRules(ruleConf: RuleConfWithPlugins)(pluginConf: ApiGroupPluginConf): Future[ExtendRules] = {
    val addressPrefix = java.util.Optional.of(pluginConf.addressPrefixOpt.map(_.value).getOrElse(pluginConf.name.value))
    val client = createClient(classOf[PluginRulesExtendService], addressPrefix)
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
