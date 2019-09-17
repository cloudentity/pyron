package com.cloudentity.edge.config

import io.circe._
import parser._
import io.circe.generic.auto._
import com.cloudentity.edge.accesslog.AccessLogHandler.AccessLogGlobalConf

object Conf {
  val smartHttpClientsKey = "smart-http-target-clients"
  val defaultSmartHttpClientKey = "smart-http-target-client-default"
  val defaultFixedHttpClientKey = "fixed-http-target-client-default"
  val rulesKey = "rules"

  case class AppConf(
    port: Option[Int],
    serverVerticles: Option[Int],
    rules: Option[Json],
    alivePath: Option[String],
    defaultProxyRules: Option[Json],
    defaultProxyRulesEnabled: Option[Boolean],
    docsEnabled: Option[Boolean],
    proxyHeaders: Option[ProxyHeaderConf],
    accessLog: Option[AccessLogGlobalConf],
    openApi: Option[OpenApiConf]
  )

  case class OpenApiConf(enabled: Option[Boolean], basePath: Option[String], getTimeout: Option[Int], listTimeout: Option[Int])

  case class ProxyHeaderConf(
    inputTrueClientIp: Option[String],
    outputTrueClientIp: Option[String],
    enabled: Option[Boolean]
  )

  def apply(json: String): AppConf = { // TODO throws exception
    decode[AppConf](json) match {
      case Right(conf) => conf
      case Left(err)   => throw new RuntimeException(err.getMessage)
    }
  }
}