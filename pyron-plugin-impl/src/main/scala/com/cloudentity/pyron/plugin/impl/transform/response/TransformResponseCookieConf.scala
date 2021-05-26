package com.cloudentity.pyron.plugin.impl.transform.response

import io.circe.Decoder
import io.circe.generic.auto._
import io.netty.handler.codec.http.cookie.CookieHeaderNames.SameSite

case class CookieDomain(value: String)
object CookieDomain {
  def empty: CookieDomain = CookieDomain(null)
}

case class CookiePath(value: String)
object CookiePath {
  def empty: CookiePath = CookiePath(null)
}

case class TransformResponseCookieConf(name: String,
                                       path: Option[CookiePath],
                                       domain: Option[CookieDomain],
                                       set: SetResponseCookie) {

  def matchesPathAndDomain(path: String, domain: String): Boolean =
    matchesPath(path) && matchesDomain(domain)

  def matchesPath(path: String): Boolean =
    this.path.isEmpty || this.path.map(_.value).contains(path)

  def matchesDomain(domain: String): Boolean =
    this.domain.isEmpty || this.domain.map(_.value).contains(domain)

}

object TransformResponseCookieConf {

  implicit val transformResponseCookieConfDecoder: Decoder[TransformResponseCookieConf] = Decoder[TransformResponseCookieConf]

  def apply(name: String, set: SetResponseCookie): TransformResponseCookieConf =
    TransformResponseCookieConf(name, None, None, set)

  def apply(name: String, domain: CookieDomain, set: SetResponseCookie): TransformResponseCookieConf =
    TransformResponseCookieConf(name, None, Some(domain), set)

  def apply(name: String, path: CookiePath, set: SetResponseCookie): TransformResponseCookieConf =
    TransformResponseCookieConf(name, Some(path), None, set)

}

case class SetResponseCookie(name: Option[String],
                             value: Option[String],
                             domain: Option[String],
                             path: Option[String],
                             maxAge: Option[Long],
                             httpOnly: Option[Boolean],
                             secure: Option[Boolean],
                             sameSite: Option[SameSite],
                             wrap: Option[Boolean])

object SetResponseCookie {
  def empty: SetResponseCookie = SetResponseCookie(None, None, None, None, None, None, None, None, None)
}


