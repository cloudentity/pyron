package com.cloudentity.edge.plugin

import com.cloudentity.edge.plugin.config._
import com.cloudentity.edge.cookie._
import com.cloudentity.edge.domain._
import com.cloudentity.edge.plugin.impl.cookies.{CookieConf, CookiesHelper}
import com.cloudentity.edge.plugin.impl.devices.{DevicePluginConf, DevicePluginVerticleConf, DevicesPlugin}
import com.cloudentity.tools.vertx.scala.FutureConversions
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import org.scalatest.{MustMatchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try
import io.circe.Json
import io.circe.parser.parse
import com.cloudentity.edge.domain.flow.AuthnCtx
import com.cloudentity.edge.test.TestRequestResponseCtx
import com.cloudentity.tools.vertx.http.Headers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class DevicePluginSpec extends WordSpec with MustMatchers with FutureConversions  with TestRequestResponseCtx with CookiesHelper {

  val plugin = new DevicesPlugin
  val config = DevicePluginVerticleConf("secure_cookies_enabled")

  val deviceTokenValue = "111-222-333"
  val request = emptyRequest.copy(method = HttpMethod.POST, headers =
    Headers("secure_cookies_enabled" -> List("true"),
        "Cookie" -> List(s"deviceToken=${deviceTokenValue}")))
  val requestCtx = emptyRequestCtx.copy(request = request)

  "Device Plugin" should {
    "rewrite deviceToken to body" in {
      val correctPluginConf = DevicePluginConf(List(CookieConf("deviceToken", RewriteCookie, Some(Body))))

      plugin.pluginConf = config
      val result = Try(Await.result(plugin.apply(requestCtx, correctPluginConf), 1 second))

      result.get.isAborted() mustBe (false)
      val map = getBodyAsMap(result.get.request.bodyOpt.get)
      map.get("deviceToken").get.asString.get mustBe deviceTokenValue
    }

    "rewrite deviceToken to JWT" in {
      val correctPluginConf = DevicePluginConf(List(CookieConf("deviceToken", RewriteCookie, Some(Jwt))))

      plugin.pluginConf = config
      val result = Try(Await.result(plugin.apply(requestCtx, correctPluginConf), 1 second))

      result.get.isAborted() mustBe (false)
      val authnCtx = result.get.authnCtx
      authnCtx mustBe AuthnCtx("deviceToken" -> Json.fromString(deviceTokenValue))
    }

    "not validate when invalid cookie action" in {
      plugin.pluginConf = config
      val correctPluginConf = DevicePluginConf(List(CookieConf("deviceToken", RemoveCookie, Some(Body))))

      val result = Try(plugin.validate(correctPluginConf))

      result.leftSideValue.get mustBe (ValidateFailure("Invalid cookie action configuration"))
    }
  }

  def getBodyAsMap(bodyBuffer: Buffer): Map[String, Json] = {
    parse(bodyBuffer.toString) match {
      case Right(res) => toMap(res)
      case Left(e)    => Map()
    }
  }

  def toMap(json: Json): Map[String, Json] = {
    json.asObject match {
      case Some(m) => m.toMap
      case None => Map()
    }
  }
}
