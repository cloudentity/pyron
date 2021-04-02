package com.cloudentity.pyron.config

import io.circe._
import parser._
import io.circe.generic.auto._
import com.cloudentity.pyron.accesslog.AccessLogHandler.AccessLogGlobalConf
import com.cloudentity.pyron.domain.rule.Kilobytes

object Conf {
  val smartHttpClientsKey = "smart-http-target-clients"
  val defaultSmartHttpClientKey = "smart-http-target-client-default"
  val defaultFixedHttpClientKey = "fixed-http-target-client-default"

  val appConfKey = "app"
  val apiGroupsConfKey = "apiGroups"
  val rulesConfKey = "rules"

  case class AppConf(port: Option[Int],
                     serverVerticles: Option[Int],
                     alivePath: Option[String],
                     defaultProxyRules: Option[Json],
                     defaultProxyRulesEnabled: Option[Boolean],
                     docsEnabled: Option[Boolean],
                     proxyHeaders: Option[ProxyHeaderConf],
                     accessLog: Option[AccessLogGlobalConf],
                     defaultRequestBodyMaxSize: Option[Kilobytes],
                     openApi: Option[OpenApiConf])

  case class OpenApiConf(enabled: Option[Boolean],
                         basePath: Option[String],
                         getTimeout: Option[Int],
                         listTimeout: Option[Int])

  case class ProxyHeaderConf(inputTrueClientIp: Option[String],
                             outputTrueClientIp: Option[String],
                             enabled: Option[Boolean])

  def decodeUnsafe(json: String): AppConf = {
    decode[AppConf](json) match {
      case Right(conf) => conf
      case Left(err) =>
        err match {
          case ParsingFailure(msg, ex) =>
            throw new Exception(s"Could not parse configuration: $msg", ex)
          case DecodingFailure(msg, ops) =>
            throw new Exception(s"Could not decode configuration attribute at 'app${CursorOp.opsToPath(ops)}': $msg")
        }
    }
  }
}