package com.cloudentity.pyron.domain.flow

import io.vertx.core.http.HttpServerRequest

import java.net.URL
import scala.util.Try

case class TargetHost(value: String) extends AnyVal
sealed trait TargetService
case class StaticService(host: TargetHost, port: Int, ssl: Boolean) extends TargetService
case class DiscoverableService(serviceName: ServiceClientName) extends TargetService
case class RerouteService(rewritePath: RewritePath) extends TargetService

sealed trait TargetServiceRule
case class StaticServiceRule(host: TargetHost, port: Int, ssl: Boolean) extends TargetServiceRule
case class DiscoverableServiceRule(serviceName: ServiceClientName) extends TargetServiceRule
case class RerouteServiceRule(rewritePath: RewritePath) extends TargetServiceRule
case object ProxyServiceRule extends TargetServiceRule

object TargetService {
  private val DEFAULT_PORT = 80
  def apply(rule: TargetServiceRule, req: HttpServerRequest): TargetService =
    rule match {
      case StaticServiceRule(host, port, ssl) => StaticService(host, port, ssl)
      case DiscoverableServiceRule(serviceName) => DiscoverableService(serviceName)
      case ProxyServiceRule => readStaticService(req)
      case RerouteServiceRule(rewritePath) => RerouteService(rewritePath)
    }

  def Static(host: TargetHost, port: Int, ssl: Boolean): TargetService =
    StaticService(host, port, ssl)

  def Discoverable(serviceName: ServiceClientName): TargetService =
    DiscoverableService(serviceName)

  private def readStaticService(req: HttpServerRequest): StaticService = {
    val ssl = req.isSSL
    if (Option(req.host()).isDefined) {
      Option(req.host()).get.split(':').toList match {
        case h :: Nil => StaticService(TargetHost(h), DEFAULT_PORT, ssl)
        case h :: p :: Nil => StaticService(TargetHost(h), Integer.parseInt(p), ssl)
        case _ => StaticService(TargetHost("malformed-host-header"), DEFAULT_PORT, ssl)
      }
    } else Try(new URL(req.absoluteURI())).map(url => {
      val port = if (url.getPort == -1) DEFAULT_PORT else url.getPort
      StaticService(TargetHost(url.getHost), port, ssl)
    })
      // it should never fail since you can't create HttpServerRequest with invalid URI
      .toOption.getOrElse(StaticService(TargetHost("malformed-url"), DEFAULT_PORT, ssl))
  }
}