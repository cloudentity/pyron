package com.cloudentity.pyron.domain.flow

import com.cloudentity.pyron.domain.http.{Headers, OriginalRequest, TargetRequest}
import com.cloudentity.pyron.rule.PreparedPathRewrite
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject

import scala.util.matching.Regex

case class PathPrefix(value: String) extends AnyVal
case class PathPattern(value: String) extends AnyVal
case class RewritePath(value: String) extends AnyVal
case class RewriteMethod(value: HttpMethod) extends AnyVal

case class ServiceClientName(value: String) extends AnyVal
case class SmartHttpClientConf(value: JsonObject)
case class FixedHttpClientConf(value: JsonObject)

case class BasePath(value: String) extends AnyVal
case class DomainPattern(value: String) {
  lazy val regex = new Regex("^" + value.replace("*", "[^\\.]+") + "$")
}

case class EndpointMatchCriteria(method: HttpMethod, rewrite: PreparedPathRewrite)

case class ProxyHeaders(headers: Map[String, List[String]], trueClientIp: String)
