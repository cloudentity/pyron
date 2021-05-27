package com.cloudentity.pyron.plugin.impl.transform.response

import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.generic.semiauto._
import io.netty.handler.codec.http.cookie.CookieHeaderNames.SameSite

import scala.util.Try

case class CookieDomain(value: String)
object CookieDomain {
  def empty: CookieDomain = CookieDomain(null)
}

case class CookiePath(value: String)
object CookiePath {
  def empty: CookiePath = CookiePath(null)
}

case class TransformResponseCookieConf(name: String,
                                       domain: Option[String],
                                       path: Option[String],
                                       set: SetResponseCookie) {

  def matchesPathAndDomain(path: String, domain: String): Boolean =
    matchesPath(path) && matchesDomain(domain)

  def matchesPath(path: String): Boolean =
    Option(this.path).map(p => p.isEmpty || p.contains(path))
      .getOrElse(Option(path).isEmpty)

  def matchesDomain(domain: String): Boolean =
    Option(this.domain).map(d => d.isEmpty || d.contains(domain))
      .getOrElse(Option(domain).isEmpty)

}

object TransformResponseCookieConf {

  def apply(name: String, set: SetResponseCookie): TransformResponseCookieConf =
    TransformResponseCookieConf(name, None, None, set)

  implicit val cookieDomainEnc: Encoder[CookieDomain] = deriveEncoder[CookieDomain]

  implicit val cookieDomainDec: Decoder[CookieDomain] = deriveDecoder[CookieDomain]

  implicit val cookiePathEnc: Encoder[CookiePath] = deriveEncoder[CookiePath]

  implicit val cookiePathDec: Decoder[CookiePath] = deriveDecoder[CookiePath]

  implicit lazy val sameSiteEnc: Encoder[SameSite] = Encoder.encodeString.contramap(_.toString)

  implicit lazy val sameSiteDec: Decoder[SameSite] = Decoder.decodeString.emap { str =>
    Try { SameSite.valueOf(str) }
      .toEither.left.map(v => s"Invalid SameSite value: $v")
  }

  implicit val setResponseCookieEnc: Encoder[SetResponseCookie] = deriveEncoder[SetResponseCookie]

  implicit val setResponseCookieDec: Decoder[SetResponseCookie] = (c: HCursor) => for {
    name <- decString("name", c)
    value <- decString("value", c)
    domain <- decString("domain", c)
    path <- decString("path", c)
    maxAge <- decLong("maxAge", c)
    httpOnly <- decBoolean("httpOnly", c)
    secure <- decBoolean("secure", c)
    sameSite <- decSameSite("sameSite", c)
    wrap <- decBoolean("wrap", c)
  } yield SetResponseCookie(name, value, domain, path, maxAge, httpOnly, secure, sameSite, wrap)

  implicit val transformResponseCookieConfEnc: Encoder[TransformResponseCookieConf] = deriveEncoder[TransformResponseCookieConf]

  implicit val transformResponseCookieConfDec: Decoder[TransformResponseCookieConf] = (c: HCursor) => {
    for {
      name <- Right(c.downField("name").focus.map(_.asString).get)
      domain <- decString("domain", c)
      path <- decString("path", c)
      set <- c.downField("set").focus.map(v => setResponseCookieDec.decodeJson(v)).get
    } yield TransformResponseCookieConf(name.get, domain, path, set)
  }

  // decode explicit null as null and undefined as None
  def dec[A](name: String, c: HCursor, f: Json => Result[Option[A]]): Result[Option[A]] =
    c.downField(name).focus.map(
      v => if (v.isNull) Right(null) else f(v)
    ).getOrElse(Right(None))

  def decString(name: String, c: HCursor): Result[Option[String]] =
    dec[String](name, c, v => v.asString
      .map(v => Right(Option(v)))
      .getOrElse(Left(DecodingFailure(s"Failed to decode String: $v", c.history))))

  def decBoolean(name: String, c: HCursor): Result[Option[Boolean]] =
    dec[Boolean](name, c, v => v.asBoolean
      .map(v => Right(Option(v)))
      .getOrElse(Left(DecodingFailure(s"Failed to decode Boolean: $v", c.history))))

  def decLong(name: String, c: HCursor): Result[Option[Long]] =
    dec[Long](name, c, v => v.asNumber.flatMap(_.toLong)
      .map(v => Right(Option(v)))
      .getOrElse(Left(DecodingFailure(s"Failed to decode Long: $v", c.history))))

  def decSameSite(name: String, c: HCursor): Result[Option[SameSite]] =
    dec[SameSite](name, c, s => {
      s.as[SameSite].fold(Left(_), v => Right(Option(v)))
    })


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


