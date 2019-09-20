package com.cloudentity.edge.rule

import java.util.Optional

import io.circe.Json
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import com.cloudentity.edge.config.Conf
import com.cloudentity.edge.config.Conf.AppConf
import com.cloudentity.edge.domain.Codecs._
import com.cloudentity.edge.domain.flow.{PluginConf, PluginName, ProxyServiceRule}
import com.cloudentity.edge.domain.rule.RuleConfWithPlugins
import com.cloudentity.edge.plugin.{ExtendRules, PluginRulesExtendService, PluginService}
import com.cloudentity.edge.rule.RulesConfReader.{RuleDecodingError, RuleErrors}
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import com.cloudentity.tools.vertx.json.JsonExtractor
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.vertx.core.json.{JsonArray, JsonObject}
import io.vertx.core.{Future => VxFuture}
import org.slf4j.LoggerFactory
import scalaz.{-\/, \/-}

import scala.concurrent.Future

trait RulesStore {
  @VertxEndpoint
  def getDeprecatedRules(): VxFuture[List[Rule]]

  @VertxEndpoint
  def getDeprecatedRuleConfsWithPlugins(): VxFuture[List[RuleConfWithPlugins]]

  @VertxEndpoint
  def decodeRules(tag: String, rules: Json): VxFuture[(List[Rule], List[RuleConfWithPlugins])]

  @VertxEndpoint
  def decodeRuleConfsWithPlugins(tag: String, rules: Json): VxFuture[List[RuleConfWithPlugins]]
}

case class RulesChanged(rules: List[Rule], confs: List[RuleConfWithPlugins])

class RulesStoreVerticle extends ScalaServiceVerticle with RulesStore {
  val log = LoggerFactory.getLogger(this.getClass)

  private val appConfPath = "app"

  private def getAppConf(): Future[AppConf] =
    getConfService.getConf(appConfPath).toScala().flatMap(decodeAppConf)

  private def decodeAppConf(conf: JsonObject): Future[AppConf] = {
    decode[AppConf](conf.toString) match {
      case Right(appConf) => Future.successful(appConf)
      case Left(err)      => Future.failed(err)
    }
  }

  def decodeRules(tag: String, rules: Json): VxFuture[(List[Rule], List[RuleConfWithPlugins])] =
    decodeRuleConfsWithPlugins(tag, rules).toScala.flatMap(confs => buildRules(confs).map((_, confs))).toJava

  def decodeRuleConfsWithPlugins(tag: String, rules: Json): VxFuture[List[RuleConfWithPlugins]] =
    readRuleConfs(Some(rules), tag).toJava()

  override def getDeprecatedRules(): VxFuture[List[Rule]] =
    getAppConf().flatMap(readRules).map(_._1).toJava()

  override def getDeprecatedRuleConfsWithPlugins(): VxFuture[List[RuleConfWithPlugins]] =
    getAppConf().flatMap(readRules).map(_._2).toJava()

  private def extractRulesConfig(root: JsonObject): AnyRef =
    JsonExtractor.resolve(root, appConfPath).flatMap(appConf => Optional.ofNullable(appConf.getValue(Conf.rulesKey))).orElse(new JsonArray())

  private def readRuleConfs(rulesConfOpt: Option[Json], tag: String, failOnEmpty: Boolean = true): Future[List[RuleConfWithPlugins]] = {
    rulesConfOpt match {
      case Some(rulesConf) => readRulesConf(rulesConf, tag)
      case None            => failOnEmpty match {
        case true => Future.failed(new Exception(s"Could not find ${tag} rules configuration at '" + appConfPath + "'"))
        case false => Future.successful(Nil)
      }
    }
  }

  private def readRulesConf(rulesConf: Json, tag: String): Future[List[RuleConfWithPlugins]] =
    RulesConfReader.read(rulesConf.toString) match {
      case \/-(rules) =>
        log.info(s"Rules (${tag}) configuration:\n${rules.map(rule => s"   ${rule.asJson.noSpaces}").mkString("\n")}")
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
        log.error(s"Failure on plugin config validations: $errors") // TODO fix error format
        Future.failed(new Exception(s"Failure on plugin config validations"))
    }

  private def readRules(conf: AppConf): Future[(List[Rule], List[RuleConfWithPlugins])] = {
    for {
      standardRules <- readStandardRules(conf)
      defaultProxyRules <- readDefaultProxyRules(conf)
      onlyProxyRules <- filterOnlyProxyRules(defaultProxyRules)
    } yield standardRules ++ onlyProxyRules
  }.flatMap { confs =>
    buildRules(confs).map((_, confs))
  }

  private def readStandardRules(conf: AppConf) = readRuleConfs(conf.rules, "standard")

  private def readDefaultProxyRules(conf: AppConf) = {
    if (conf.defaultProxyRulesEnabled.getOrElse(false)) {
      log.debug("Default proxy rules are enabled. Reading config.")
      readRuleConfs(conf.defaultProxyRules, "default", false)
    } else {
      log.debug("Default proxy rules are disabled. Skipping.")
      Future.successful(Nil)
    }
  }

  private def filterOnlyProxyRules(rules: List[RuleConfWithPlugins]) = {
    if (rules.find(r => r.rule.target != ProxyServiceRule).isDefined) {
      log.warn(s"There are non proxy rules in default proxy rules configuration. Ignoring them")
    }
    Future.successful(rules.filter(r => r.rule.target == ProxyServiceRule))
  }

}
