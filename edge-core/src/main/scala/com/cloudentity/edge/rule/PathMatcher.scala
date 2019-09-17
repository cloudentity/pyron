package com.cloudentity.edge.rule

import com.cloudentity.edge.domain._
import com.cloudentity.edge.domain.flow.{PathMatching, PathParams}

object PathMatcher {
  def makeMatch(path: String, matcher: PathMatching): Option[PathParams] =
    matcher.regex.findFirstMatchIn(path).flatMap[PathParams] { mtch =>
      if (mtch.groupCount == matcher.paramNames.size)
        Some(
          PathParams(
            matcher.paramNames.map { name =>
              name.value -> mtch.group(name.value)
            }.toMap
          )
        )
      else None
    }
}