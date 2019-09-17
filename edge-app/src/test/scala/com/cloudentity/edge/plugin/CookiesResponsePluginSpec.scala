package com.cloudentity.edge.plugin

import com.cloudentity.edge.plugin.config._
import com.cloudentity.edge.cookie._
import com.cloudentity.edge.domain.{http, _}
import com.cloudentity.edge.domain.http.{ApiResponse, ClientCookies}
import com.cloudentity.edge.plugin.impl.cookies._
import com.cloudentity.edge.test.TestRequestResponseCtx
import com.cloudentity.tools.vertx.scala.FutureConversions
import com.cloudentity.tools.vertx.http.Headers
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

@RunWith(classOf[JUnitRunner])
class CookiesResponsePluginSpec   extends WordSpec with MustMatchers with FutureConversions with TestRequestResponseCtx {
  val log = LoggerFactory.getLogger(this.getClass)

  val plugin = new CookieResponsePlugin
  val defaultCookieSettings = CookieSettings("/", "some.domain.com", false, true, Some(123))
  val expiredCookieSettings = CookieSettings("/", "some.domain.com", false, true, Some(0))

  val config = CookieRequestVerticleConf("secure_cookies_enabled", Map("token" -> CookiesConfig("token", "token", defaultCookieSettings)))
  val responseBodyWithToken = Buffer.buffer("{\"token\":\"1-2-3\" , \"otherToken\":\"3-2-1\"}")
  val responseBodyWithOtherToken = Buffer.buffer("{\"otherToken\":\"3-2-1\"}")
  val emptyBody = Buffer.buffer("{}")

  val apiResponseWithBody = ApiResponse(body = responseBodyWithToken, statusCode = 201, headers = Headers.of("Content-Type" -> "application/json"))

  val request = emptyRequest.copy(method = HttpMethod.POST, headers = Headers("secure_cookies_enabled" -> List("true")))
  val responseCtx = emptyResponseCtx.copy(request = request)

  val pluginConf = CookiePluginConf(List(CookieConf("token", RewriteCookie, None)))

  "CookieResponsePlugin" should {

    "add cookie" in {
      plugin.pluginConf = config
      val result = Try(Await.result(plugin.apply(responseCtx.copy(response = apiResponseWithBody), pluginConf), 1 second))

      result.isSuccess mustBe (true)
      result.get.response.cookies mustBe (Some(ClientCookies("", List(CookieUtils.buildCookie("token", "1-2-3", defaultCookieSettings)))))
      result.get.response.body mustBe (responseBodyWithOtherToken)
    }

    "skip adding cookie when token claim not found" in {
      plugin.pluginConf = config

      val responseBodyWithEmbeddedToken = Buffer.buffer("{\"attr1\":{\"token\":\"111\"},\"otherAttr\":\"222\"}")
      val responseWithBody = ApiResponse(body = responseBodyWithEmbeddedToken, statusCode = 201, headers = Headers.of("Content-Type" -> "application/json"))

      val result = Try(Await.result(plugin.apply(responseCtx.copy(response = responseWithBody), pluginConf), 1 second))

      result.isSuccess mustBe (true)
      result.get.response.cookies mustBe None
      result.get.response.body mustBe (responseBodyWithEmbeddedToken)
    }

    "remove cookie" in {
      plugin.pluginConf = config
      val pluginConf = CookiePluginConf(List(CookieConf("token", RemoveCookie, None)))
      val result = Try(Await.result(plugin.apply(responseCtx.copy(response = apiResponseWithBody), pluginConf), 1 second))

      result.isSuccess mustBe (true)
      result.get.response.cookies mustBe (Some(ClientCookies("", List(CookieUtils.buildCookie("token", "", expiredCookieSettings)))))
    }

    "skip add cookie when response body has no attribute to transform to cookie" in {
      val apiResponseWithoutRequiredAttribute = ApiResponse(body = responseBodyWithOtherToken, statusCode = 201, headers = Headers.of("Content-Type" -> "application/json"))

      plugin.pluginConf = config
      val result = Try(Await.result(plugin.apply(responseCtx.copy(response = apiResponseWithoutRequiredAttribute), pluginConf), 1 second))

      result.isSuccess mustBe (true)
      result.get.response.cookies mustBe (None)
      result.get.response.body mustBe (responseBodyWithOtherToken)
    }

    "fail when plugin configuration has an empty list of cookies" in {
      plugin.pluginConf = config
      val pluginConf = CookiePluginConf(List())
      val value1: Try[ValidateResponse] = Try(plugin.validate(pluginConf))

      value1.isSuccess mustBe (true)
      value1.leftSideValue.get mustBe (ValidateFailure("Cookies configuration is missing"))
    }
  }
}