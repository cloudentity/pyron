package com.cloudentity.pyron.domain.http

import io.vertx.core.http.Cookie.cookie
import io.vertx.core.http.CookieSameSite

import java.util
import scala.collection.JavaConverters._

case class Cookie(name: String,
                  domain: String,
                  path: String,
                  value: String,
                  isHttpOnly: Boolean,
                  isSecure: Boolean,
                  sameSite: SameSite)

object Cookie {

  type Cookies = Map[String, Cookie]

  def cookies(from: util.Map[String, io.vertx.core.http.Cookie]): Cookies =
    from.asScala.mapValues(apply).toMap

  def parse(s: String): Option[Cookie] = {
    val splitAt = s.indexOf('=')
    if (splitAt > 0) {
      val name = s.substring(0, splitAt)
      val value = s.substring(splitAt + 1)
      try {
        Some(Cookie(cookie(name, value)))
      }
      catch {
        case _: Throwable => None
      }
    } else None
  }

  def apply(from: io.vertx.core.http.Cookie): Cookie = Cookie(
    from.getName,
    from.getDomain,
    from.getPath,
    from.getValue,
    from.isHttpOnly,
    from.isSecure,
    SameSite(from.getSameSite)
  )

}

sealed trait SameSite

object SameSite {
  def parse(name: String): Option[SameSite] = try {
    Some(SameSite(CookieSameSite.valueOf(name)))
  } catch {
    case _: Throwable => Option.empty
  }

  def apply(from: CookieSameSite): SameSite = from match {
    case CookieSameSite.LAX => Lax
    case CookieSameSite.STRICT => Strict
    case CookieSameSite.NONE => None
  }

  case object None extends SameSite {
    override def toString: String = CookieSameSite.NONE.toString
  }

  case object Lax extends SameSite {
    override def toString: String = CookieSameSite.LAX.toString
  }

  case object Strict extends SameSite {
    override def toString: String = CookieSameSite.STRICT.toString
  }

}