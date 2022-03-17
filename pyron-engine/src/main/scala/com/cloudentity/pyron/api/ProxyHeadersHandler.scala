package com.cloudentity.pyron.api

import com.cloudentity.pyron.config.Conf.ProxyHeaderConf
import com.cloudentity.pyron.domain.flow.ProxyHeaders
import io.vertx.core.MultiMap
import io.vertx.ext.web.RoutingContext

import scala.util.Try
import scala.collection.JavaConverters._

object ProxyHeadersHandler {
  val proxyHeadersKey = "proxyHeaders"
  val trueClientIpKey = "trueClientIp"

  val defaultTrueClientIpHeader = "X-Real-IP"
  val xForwardedForHeader   = "X-Forwarded-For"
  val xForwardedHostHeader  = "X-Forwarded-Host"
  val xForwardedProtoHeader = "X-Forwarded-Proto"

  def handle(proxyHeaderNames: Option[ProxyHeaderConf])(ctx: RoutingContext): Unit = {
    val req = ctx.request()
    val headers = proxyHeaders(req.headers, req.remoteAddress().host(), Option(req.host()), req.isSSL, proxyHeaderNames.getOrElse(ProxyHeaderConf(None, None, None)))
    ctx.put(proxyHeadersKey, headers)
    ctx.next()
  }

  def proxyHeaders(headers: MultiMap, remoteHost: String, host: Option[String], ssl: Boolean, proxyHeaderNames: ProxyHeaderConf): ProxyHeaders = {
    val remoteIp         = remoteHost
    val remoteHostOpt    = host
    val protocol         = if (ssl) "https" else "http"

    val realIpInHeader   = proxyHeaderNames.inputTrueClientIp.getOrElse(defaultTrueClientIpHeader)
    val realIpOutHeader  = proxyHeaderNames.outputTrueClientIp.getOrElse(defaultTrueClientIpHeader)
    val realIpHeaderVals = headers.getAll(realIpInHeader).asScala.toList

    val realIp           = getFirstValueWithCommaSeparator(headers, realIpInHeader)
                             .orElse(getFirstValueWithCommaSeparator(headers, xForwardedForHeader))
                             .getOrElse(remoteIp)

    val proxyHeaders: Map[String, List[String]] =
      if (proxyHeaderNames.enabled.getOrElse(true)) {
        Map(
          xForwardedForHeader   -> (headers.getAll(xForwardedForHeader).asScala.toList ::: List(remoteIp)),
          xForwardedHostHeader  -> (headers.getAll(xForwardedHostHeader).asScala.toList ::: remoteHostOpt.toList),
          xForwardedProtoHeader -> (headers.getAll(xForwardedProtoHeader).asScala.toList ::: List(protocol)),
          realIpOutHeader       -> realIpHeaderVals.headOption.fold(List(realIp))(_ => realIpHeaderVals)
        )
      } else Map()

    ProxyHeaders(
      headers = proxyHeaders,
      trueClientIp = realIp
    )
  }

  // gets first header value taking into account that a single header can contain multiple comma-separated values (like in nging),
  // e.g. given 'X-Forwarded-For: 203.0.113.195, 70.41.3.18' 'X-Forwarded-For: 150.172.238.178' extracts '203.0.113.195'
  private def getFirstValueWithCommaSeparator(headers: MultiMap, headerName: String): Option[String] =
    headers.getAll(headerName).asScala.toList.flatMap(_.split(",")).map(_.trim).headOption

  def getProxyHeaders(ctx: RoutingContext): Option[ProxyHeaders] =
    Try(ctx.get[ProxyHeaders](proxyHeadersKey)).toOption.flatMap(Option.apply)
}
