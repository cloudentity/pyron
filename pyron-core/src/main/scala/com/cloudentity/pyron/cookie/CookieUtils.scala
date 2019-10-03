package com.cloudentity.pyron.cookie

import io.netty.handler.codec.http.cookie.{Cookie, DefaultCookie, ServerCookieDecoder, ServerCookieEncoder}

import scala.collection.JavaConverters._
import scala.util.Try


object CookieUtils {

  def encode(cookie: Cookie): String = ServerCookieEncoder.STRICT.encode(cookie)

  def decode(header: String): List[Cookie] = ServerCookieDecoder.STRICT.decode(header).asScala.toList

  def encode(cookies: List[Cookie]): List[String] =
    ServerCookieEncoder.STRICT.encode(cookies.asJava).asScala.toList

  def buildExpiredCookie(name: String, settings: CookieSettings): Cookie = {
    val cookie = new DefaultCookie(name, "")
    cookie.setMaxAge(0.toLong)
    cookie.setDomain(settings.domain)
    cookie.setPath(settings.path)
    cookie.setHttpOnly(settings.httpOnly)
    cookie.setSecure(settings.secure)
    cookie
  }

  def buildCookie(name: String, value: String, settings: CookieSettings): Cookie = {
    val cookie = new DefaultCookie(name, value)
    settings.maxAge.filter(_ > 0).map(cookie.setMaxAge(_))
    cookie.setDomain(settings.domain)
    cookie.setPath(settings.path)
    cookie.setHttpOnly(settings.httpOnly)
    cookie.setSecure(settings.secure)
    cookie
  }

  def parseCookies(cookieHeaders: List[String]): Map[String, String] =
    cookieHeaders.flatMap { header =>
      val cookies: Iterable[Cookie] = Try(ServerCookieDecoder.STRICT.decode(header)).toOption.map(_.asScala).getOrElse(Nil)
      cookies.map(c => c.name() -> c.value())
    }.toMap

}
