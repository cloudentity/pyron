package com.cloudentity.edge.cookie

case class CookieSettings(path: String, domain: String, secure: Boolean, httpOnly: Boolean, maxAge: Option[Long] = None)

case class CookiesConfig(cookieName: String, attributeName: String, cookieSettings: CookieSettings)

sealed trait CookieAction
case object RewriteCookie             extends CookieAction
case object RemoveCookie              extends CookieAction

sealed trait InjectPlace
case object Body                      extends InjectPlace
case object Header                    extends InjectPlace
case object Jwt                       extends InjectPlace
