package com.cloudentity.pyron.rule

import com.cloudentity.pyron.domain.flow.{BasePath, EndpointMatchCriteria}
import io.vertx.core.http.HttpMethod

object RuleMatcher {
  sealed trait MatchResult
  case class Match(appliedRewrite: AppliedPathRewrite) extends MatchResult
  case object NoMatch extends MatchResult

  def makeMatch(method: HttpMethod, path: String, basePath: BasePath, criteria: EndpointMatchCriteria): MatchResult = {
    if (criteria.method == method) {
      val relativePath = path.drop(basePath.value.length)
      criteria.rewrite.applyRewrite(relativePath)
        .fold[MatchResult](NoMatch)(Match)
    } else {
      NoMatch
    }
  }
}
