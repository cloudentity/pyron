package com.cloudentity.edge.cookie

import java.util.Date

import io.netty.handler.codec.DateFormatter
import io.netty.handler.codec.http.cookie.DefaultCookie
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class CookieUtilsSpec extends WordSpec with MustMatchers {

  val cookieName = "token"
  val cookieValue = "123"
  val maxAge = 3600
  val domain = "example.com"
  val path = "/"
  val isSecure = true
  val isHttpOnly = true

  "Cookie Utils" should {

    "encode cookie" in {
      val cookie = new DefaultCookie(cookieName, cookieValue)
      cookie.setDomain(domain)
      cookie.setPath(path)
      cookie.setMaxAge(maxAge.toLong)
      cookie.setSecure(isSecure)
      cookie.setHttpOnly(isHttpOnly)

      val encoded = CookieUtils.encode(cookie)
      assertEncoded(encoded, cookieName, cookieValue, maxAge, path, domain)
    }

    "build expired cookie" in {
      val cookie = CookieUtils.buildExpiredCookie(cookieName, CookieSettings(path, domain, isSecure, isHttpOnly))
      val encoded = CookieUtils.encode(cookie)
      assertEncoded(encoded, cookieName, "", 0, path, domain)
    }

    "build cookie" in {
      val cookie = CookieUtils.buildCookie(cookieName, cookieValue, CookieSettings(path, domain, isSecure, isHttpOnly, Some(maxAge)))
      val encoded = CookieUtils.encode(cookie)
      assertEncoded(encoded, cookieName, cookieValue, maxAge, path, domain)
    }
  }

  def assertEncoded(encoded: String, cookieName: String, cookieValue: String, maxAge: Int, path: String, domain: String) = {
    encoded must be(s"${cookieName}=${cookieValue}; Max-Age=${maxAge}; Expires=${expires(maxAge)}; Path=${path}; Domain=${domain}; Secure; HTTPOnly")
  }

  def expires(maxAge: Int): String = {
    DateFormatter.format(new Date(maxAge * 1000 + System.currentTimeMillis))
  }
}
