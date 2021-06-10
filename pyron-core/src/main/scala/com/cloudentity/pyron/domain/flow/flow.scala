package com.cloudentity.pyron.domain.flow

import com.cloudentity.pyron.rule.PreparedPathRewrite
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject

case class PathPrefix(value: String) extends AnyVal
case class PathPattern(value: String) extends AnyVal
case class RewritePath(value: String) extends AnyVal
case class RewriteMethod(value: HttpMethod) extends AnyVal

case class EndpointMatchCriteria(method: HttpMethod, rewrite: PreparedPathRewrite)
case class ServiceClientName(value: String) extends AnyVal

case class SmartHttpClientConf(value: JsonObject)
case class FixedHttpClientConf(value: JsonObject)

case class ProxyHeaders(headers: Map[String, List[String]], trueClientIp: String)
