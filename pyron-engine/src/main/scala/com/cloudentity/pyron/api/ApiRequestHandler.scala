package com.cloudentity.pyron.api

import com.cloudentity.pyron.apigroup.ApiGroup
import com.cloudentity.pyron.domain.flow._
import com.cloudentity.pyron.domain.http.TargetRequest
import com.cloudentity.pyron.rule.RuleMatcher.{Match, NoMatch}
import com.cloudentity.pyron.rule.{ApiGroupMatcher, AppliedRewrite, Rule, RuleMatcher}
import io.vertx.core.http.HttpServerRequest
import io.vertx.ext.web.RoutingContext

import scala.annotation.tailrec

object ApiRequestHandler {
  def setAuthnCtx(ctx: RoutingContext, authnCtx: AuthnCtx): Unit =
    RoutingCtxData.updateFlowState(ctx, _.copy(authnCtx = Some(authnCtx)))

  def setRule(ctx: RoutingContext, rule: Rule): Unit =
    RoutingCtxData.updateFlowState(ctx, _.copy(rule = Some(rule)))

  def setAborted(ctx: RoutingContext, aborted: Boolean): Unit =
    RoutingCtxData.updateFlowState(ctx, _.copy(aborted = Some(aborted)))

  def setFailure(ctx: RoutingContext, failure: Option[FlowFailure]): Unit =
    RoutingCtxData.updateFlowState(ctx, _.copy(failure = failure))

  def addExtraAccessLogItems(ctx: RoutingContext, items: AccessLogItems): Unit =
    RoutingCtxData.updateFlowState(ctx, state => state.copy(extraAccessLogs = state.extraAccessLogs.merge(items)))

  def addProperties(ctx: RoutingContext, props: Properties): Unit =
    RoutingCtxData.updateFlowState(ctx, state => state.copy(properties = Properties(state.properties.toMap ++ props.toMap)))

  case class RuleWithAppliedRewrite(rule: Rule, appliedRewrite: AppliedRewrite)

  def findMatchingApiGroup(apiGroups: List[ApiGroup], vertxRequest: HttpServerRequest): Option[ApiGroup] = {
    val path = Option(vertxRequest.path()).getOrElse("")
    val hostOpt = Option(vertxRequest.host())
    apiGroups.find { group => ApiGroupMatcher.makeMatch(hostOpt, path, group.matchCriteria) }
  }

  def findMatchingRule(apiGroup: ApiGroup, vertxRequest: HttpServerRequest): Option[RuleWithAppliedRewrite] = {
    @tailrec
    def loop(basePath: BasePath, rules: List[Rule]): Option[RuleWithAppliedRewrite] = rules match {
      case rule :: tail =>
        val criteria = rule.conf.criteria
        val path = Option(vertxRequest.path()).getOrElse("")
        RuleMatcher.makeMatch(vertxRequest.method(), path, basePath, criteria) match {
          case Match(appliedRewrite) => Some(RuleWithAppliedRewrite(rule, appliedRewrite))
          case NoMatch => loop(basePath, tail)
        }
      case Nil => None
    }

    loop(apiGroup.matchCriteria.basePathResolved, apiGroup.rules)
  }

  def withProxyHeaders(proxyHeaders: ProxyHeaders)(req: TargetRequest): TargetRequest =
    req.withHeaderValues(proxyHeaders.headers)
}
