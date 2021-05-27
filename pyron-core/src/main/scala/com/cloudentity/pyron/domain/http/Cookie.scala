package com.cloudentity.pyron.domain.http

import io.vertx.core.http.{CookieSameSite, Cookie => VertxCookie}

case class Cookie(name: String,
                  value: String,
                  domain: Option[String],
                  path: Option[String],
                  isHttpOnly: Boolean,
                  isSecure: Boolean,
                  sameSite: Option[CookieSameSite])

object Cookie {

  type Cookies = Map[String, Cookie]

  def apply(from: VertxCookie): Cookie = Cookie(
    from.getName,
    from.getValue,
    Option(from.getDomain),
    Option(from.getPath),
    from.isHttpOnly,
    from.isSecure,
    Option(from.getSameSite)
  )

}