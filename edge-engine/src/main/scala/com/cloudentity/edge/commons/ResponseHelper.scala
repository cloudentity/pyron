package com.cloudentity.edge.commons

import io.circe.JsonObject
import io.circe.syntax._
import io.netty.handler.codec.http.cookie.Cookie
import com.cloudentity.edge.cookie.{CookieSettings, CookieUtils}
import com.cloudentity.edge.domain.http.{ApiResponse, ClientCookies}
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.buffer.Buffer

trait ResponseHelper extends BodyUtils{
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  def removeClientCookie(name: String, settings: CookieSettings, apiResponse: ApiResponse) = {
    val expiredCookie = CookieUtils.buildExpiredCookie(name, settings)
    addCookieToResponse(expiredCookie, apiResponse)
  }

  def addClientCookie(ctx: TracingContext, cookieName: String, attributeName: String, settings: CookieSettings, apiResponse: ApiResponse): Either[String, ApiResponse] = {
    readBodyAttribute(attributeName, apiResponse).map { attrValue =>
        val cookieToAdd = CookieUtils.buildCookie(cookieName, attrValue, settings)
        addCookieToResponse(cookieToAdd, apiResponse)
    }.left.map { err => s"Failed to decode response body: ${err.getMessage}" }
  }

  def removeBodyAttribute(ctx: TracingContext, name: String, apiResponse: ApiResponse): Either[String, ApiResponse] = {
    bodyAsJsonObject(apiResponse.body).map { body =>
        val newBody: JsonObject = bodyAttributeOperation(name, "", body)(removeFromBody)
        apiResponse.copy(body = Buffer.buffer(newBody.asJson.noSpaces))
    }.left.map { err => s"Failed to decode response body: ${err.getMessage}" }
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
