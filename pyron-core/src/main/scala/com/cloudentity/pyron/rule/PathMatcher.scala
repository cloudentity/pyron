package com.cloudentity.pyron.rule

import com.cloudentity.pyron.domain.flow.{PathMatching, PathParams}

object PathMatcher {
  def makeMatch(path: String, matcher: PathMatching): Option[PathParams] = for {
    found <- matcher.regex.findFirstMatchIn(path) // if found.groupCount == matcher.paramNames.size
    paramNamesAndValues = matcher.paramNames.map(name => name.value -> found.group(name.value))
  } yield PathParams(paramNamesAndValues.toMap)
}
