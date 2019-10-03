package com.cloudentity.pyron.plugin

import io.circe.Json
import com.cloudentity.pyron.domain.rule.RuleConfWithPlugins
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import io.vertx.core.Future

case class ExtendRules(prepend: List[RuleConfWithPlugins] = Nil, append: List[RuleConfWithPlugins] = Nil)

trait PluginRulesExtendService {
  @VertxEndpoint
  def extendRules(rule: RuleConfWithPlugins, pluginConf: Json): Future[ExtendRules]
}

trait PluginRulesExtender[C] extends PluginRulesExtendService {
  protected def getFromCacheOrDecode(conf: Json): Either[Throwable, C]
  def extendRules(rule: RuleConfWithPlugins, conf: C): ExtendRules = ExtendRules()

  def handleExtendRules(rule: RuleConfWithPlugins, pluginConf: Json): Future[ExtendRules] =
    getFromCacheOrDecode(pluginConf) match {
      case Right(conf) =>
        Future.succeededFuture(extendRules(rule, conf))
      case Left(ex) =>
        Future.failedFuture(ex)
    }
}