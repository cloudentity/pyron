package com.cloudentity.pyron.domain.flow

case class PathParams(value: Map[String, String]) extends AnyVal

object PathParams {
  def empty: PathParams = PathParams(Map())

  case class PathParamName(value: String) extends AnyVal
}

case class PathParamName(value: String) extends AnyVal

