package com.cloudentity.pyron.domain.http

import io.vertx.core.http.{Cookie => VertxCookie}
import io.vertx.core.http.CookieSameSite

case class Cookie(name: String,
                  value: String,
                  domain: Option[String],
                  path: Option[String],
                  isHttpOnly: Boolean,
                  isSecure: Boolean,
                  sameSite: Option[CookieSameSite])

object Cookie {

  type Cookies = List[Cookie]

  def apply(from: VertxCookie): Cookie = Cookie(
    from.getName,
    from.getValue,
    Option(from.getDomain),
    Option(from.getPath),
    from.isHttpOnly,
    from.isSecure,
    Option(from.getSameSite)
  )

  def find(name: String)(cookies: Cookies): Option[Cookie] =
    cookies.find(_.name == name)

  def find(name: String, domain: Option[String], path: Option[String])(cookies: Cookies): Option[Cookie] =
    cookies.find(cookie => cookie.name == name &&
      domain == cookie.domain &&
      path == cookie.path
    )

  def parse(s: String): Option[Cookie] = {
    val splitAt = s.indexOf('=')
    if (splitAt > 0) {
      val name = s.substring(0, splitAt)
      val value = s.substring(splitAt + 1)
      try {
        Some(Cookie(VertxCookie.cookie(name, value)))
      }
      catch {
        case _: Throwable => None
      }
    } else None
  }

}
