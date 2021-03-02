package com.cloudentity.pyron.rule

import com.cloudentity.pyron.domain.flow.{BasePath, EndpointMatchCriteria, PathParams}
import io.vertx.core.http.HttpMethod

object RuleMatcher {
  sealed trait MatchResult
    case class Match(pathParams: PathParams) extends MatchResult
    case object NoMatch extends MatchResult

  def makeMatch(method: HttpMethod, path: String, basePath: BasePath, criteria: EndpointMatchCriteria): MatchResult =
    if (criteria.method == "*" || criteria.method == method) {
      val relativePath = path.drop(basePath.value.length)
      RewriteUtil
        .applyRewrite(relativePath, criteria.rewrite)
        .fold[MatchResult](NoMatch)(rew => Match(rew.pathParams))
    } else {
      NoMatch
    }
}
