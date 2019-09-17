package com.cloudentity.edge.api

import com.cloudentity.edge.config.Conf.ProxyHeaderConf
import com.cloudentity.edge.domain.flow.ProxyHeaders
import io.vertx.core.MultiMap
import io.vertx.core.http.HttpServerRequest
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
    ctx.next
  }

  def proxyHeaders(headers: MultiMap, remoteHost: String, host: Option[String], ssl: Boolean, proxyHeaderNames: ProxyHeaderConf): ProxyHeaders = {
    val remoteIp      = remoteHost
    val remoteHostOpt = host
    val protocol      = if (ssl) "https" else "http"
    val realIp        = Option(headers.get(proxyHeaderNames.inputTrueClientIp.getOrElse(defaultTrueClientIpHeader))) match {
                          case Some(ip) => ip
                          case None => Option(headers.get("X-Forwarded-For")).getOrElse(remoteIp)
                        }

    val proxyHeaders: Map[String, List[String]] =
      if (proxyHeaderNames.enabled.getOrElse(true)) {
        Map(
          xForwardedForHeader   -> (headers.getAll(xForwardedForHeader).asScala.toList ::: List(remoteIp)),
          xForwardedHostHeader  -> (headers.getAll(xForwardedHostHeader).asScala.toList ::: remoteHostOpt.map(List(_)).getOrElse(Nil)),
          xForwardedProtoHeader -> (headers.getAll(xForwardedProtoHeader).asScala.toList ::: List(protocol)),
          proxyHeaderNames.outputTrueClientIp.getOrElse(defaultTrueClientIpHeader) -> List(realIp)
        )
      } else Map()

    ProxyHeaders(
      headers = proxyHeaders,
      trueClientIp = realIp
    )
  }

  def getProxyHeaders(ctx: RoutingContext): Option[ProxyHeaders] =
    Try(ctx.get[ProxyHeaders](proxyHeadersKey)).toOption.flatMap(Option.apply)
}
