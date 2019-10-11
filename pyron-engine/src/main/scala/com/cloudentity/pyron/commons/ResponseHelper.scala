package com.cloudentity.pyron.commons

import com.cloudentity.pyron.cookie.{CookieSettings, CookieUtils}
import com.cloudentity.pyron.domain.http.{ApiResponse, ClientCookies}
import com.cloudentity.tools.vertx.tracing.LoggingWithTracing
import io.netty.handler.codec.http.cookie.Cookie

trait ResponseHelper {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  def removeClientCookie(name: String, settings: CookieSettings, apiResponse: ApiResponse) = {
    val expiredCookie = CookieUtils.buildExpiredCookie(name, settings)
    addCookieToResponse(expiredCookie, apiResponse)
  }

  def addCookieToResponse(cookie: Cookie, apiResponse: ApiResponse): ApiResponse = {
    val newCookies = apiResponse.cookies match {
      case Some(clientCookies) => clientCookies.copy(cookies = replaceCookieIfExist(cookie, clientCookies))
      case None => ClientCookies("", List(cookie))
    }
    apiResponse.copy(cookies = Some(newCookies))
  }

  def replaceCookieIfExist(cookie: Cookie, clientCookies: ClientCookies): List[Cookie] = {
    cookie :: clientCookies.cookies.filter(_.name() != cookie.name())
  }
}
