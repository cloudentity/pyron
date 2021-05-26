package com.cloudentity.pyron.plugin.impl.transform.response

import com.cloudentity.pyron.test.TestRequestResponseCtx
import com.cloudentity.tools.vertx.http.Headers
import io.netty.handler.codec.http.cookie.CookieHeaderNames.SameSite
import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatestplus.junit.JUnitRunner

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

@RunWith(classOf[JUnitRunner])
class TransformResponseCookiePluginTest extends WordSpec with MustMatchers with TestRequestResponseCtx {

  val SET_COOKIE = "Set-Cookie"

  def calcExpires(expireAfter: Long): String =
      ZonedDateTime.now(ZoneId.of("GMT")).plusSeconds(expireAfter)
          .format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss z"))

  "TransformResponseCookiePlugin.transformCookie" should {

    "set name of matching cookies only" in {

      val newName = "newName"

      val h0 = "Foo-Header" -> "fooValue"
      val setNewName =  SetResponseCookie.empty.copy(name = Some(newName))

      val h1 = SET_COOKIE -> "cookieOne=valueOneA"
      val h1Updated = SET_COOKIE -> s"$newName=valueOneA"

      val h2 = SET_COOKIE -> "cookieOne=valueOneB; Path=/some/path"
      val h2Updated = SET_COOKIE -> s"$newName=valueOneB; Path=/some/path"

      val h3 = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com"
      val h3Updated = SET_COOKIE -> s"$newName=valueOneC; Domain=some.domain.com"

      val h4 = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com"
      val h4Updated = SET_COOKIE -> s"$newName=valueOneD; Path=/some/path; Domain=some.domain.com"

      val h5 = SET_COOKIE -> "cookieTwo=valueTwo"
      val h6 = "Bar-Header" -> "barValue"

      val headersIn = Headers.of(h0, h1, h2, h3, h4, h5, h6)

      val anyDomainAndPathConf = TransformResponseCookieConf("cookieOne", None, None, setNewName)
      TransformResponseCookiePlugin.transformCookie(headersIn, anyDomainAndPathConf) mustBe
        Headers.of(h0, h1Updated, h2Updated, h3Updated, h4Updated, h5, h6)

      val emptyDomainAndPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain.empty), setNewName)
      TransformResponseCookiePlugin.transformCookie(headersIn, emptyDomainAndPathConf) mustBe
        Headers.of(h0, h1Updated, h2, h3, h4, h5, h6)

      val somePathEmptyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), Some(CookieDomain.empty), setNewName)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathEmptyDomainConf) mustBe
        Headers.of(h0, h1, h2Updated, h3, h4, h5, h6)

      val somePathAnyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), None, setNewName)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathAnyDomainConf) mustBe
        Headers.of(h0, h1, h2Updated, h3, h4Updated, h5, h6)

      val someDomainEmptyPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain("some.domain.com")), setNewName)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainEmptyPathConf) mustBe
        Headers.of(h0, h1, h2, h3Updated, h4, h5, h6)

      val someDomainAnyPathConf = TransformResponseCookieConf("cookieOne", None, Some(CookieDomain("some.domain.com")), setNewName)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainAnyPathConf) mustBe
        Headers.of(h0, h1, h2, h3Updated, h4Updated, h5, h6)

    }

    "set value of matching cookies only" in {

      val newValue = "newValue"
      val setNewValue = SetResponseCookie.empty.copy(value = Some(newValue))

      val h0 = "Foo-Header" -> "fooValue"

      val h1 = SET_COOKIE -> "cookieOne=valueOneA"
      val h1Updated = SET_COOKIE -> s"cookieOne=$newValue"

      val h2 = SET_COOKIE -> "cookieOne=valueOneB; Path=/some/path"
      val h2Updated = SET_COOKIE -> s"cookieOne=$newValue; Path=/some/path"

      val h3 = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com"
      val h3Updated = SET_COOKIE -> s"cookieOne=$newValue; Domain=some.domain.com"

      val h4 = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com"
      val h4Updated = SET_COOKIE -> s"cookieOne=$newValue; Path=/some/path; Domain=some.domain.com"

      val h5 = SET_COOKIE -> "cookieTwo=valueTwo"
      val h6 = "Bar-Header" -> "barValue"

      val headersIn = Headers.of(h0, h1, h2, h3, h4, h5, h6)

      val anyDomainAndPathConf = TransformResponseCookieConf("cookieOne", None, None, setNewValue)
      TransformResponseCookiePlugin.transformCookie(headersIn, anyDomainAndPathConf) mustBe
        Headers.of(h0, h1Updated, h2Updated, h3Updated, h4Updated, h5, h6)

      val emptyDomainAndPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain.empty), setNewValue)
      TransformResponseCookiePlugin.transformCookie(headersIn, emptyDomainAndPathConf) mustBe
        Headers.of(h0, h1Updated, h2, h3, h4, h5, h6)

      val somePathEmptyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), Some(CookieDomain.empty), setNewValue)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathEmptyDomainConf) mustBe
        Headers.of(h0, h1, h2Updated, h3, h4, h5, h6)

      val somePathAnyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), None, setNewValue)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathAnyDomainConf) mustBe
        Headers.of(h0, h1, h2Updated, h3, h4Updated, h5, h6)

      val someDomainEmptyPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain("some.domain.com")), setNewValue)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainEmptyPathConf) mustBe
        Headers.of(h0, h1, h2, h3Updated, h4, h5, h6)

      val someDomainAnyPathConf = TransformResponseCookieConf("cookieOne", None, Some(CookieDomain("some.domain.com")), setNewValue)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainAnyPathConf) mustBe
        Headers.of(h0, h1, h2, h3Updated, h4Updated, h5, h6)

    }

    "set name and value of matching cookies only" in {

      val newName = "newName"
      val newValue = "newValue"
      val setNewNameAndValue = SetResponseCookie.empty.copy(name = Some(newName), value = Some(newValue))

      val h0 = "Foo-Header" -> "fooValue"

      val h1 = SET_COOKIE -> "cookieOne=valueOneA"
      val h1Updated = SET_COOKIE -> s"$newName=$newValue"

      val h2 = SET_COOKIE -> "cookieOne=valueOneB; Path=/some/path"
      val h2Updated = SET_COOKIE -> s"$newName=$newValue; Path=/some/path"

      val h3 = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com"
      val h3Updated = SET_COOKIE -> s"$newName=$newValue; Domain=some.domain.com"

      val h4 = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com"
      val h4Updated = SET_COOKIE -> s"$newName=$newValue; Path=/some/path; Domain=some.domain.com"

      val h5 = SET_COOKIE -> "cookieTwo=valueTwo"
      val h6 = "Bar-Header" -> "barValue"

      val headersIn = Headers.of(h0, h1, h2, h3, h4, h5, h6)

      val anyDomainAndPathConf = TransformResponseCookieConf("cookieOne", None, None, setNewNameAndValue)
      TransformResponseCookiePlugin.transformCookie(headersIn, anyDomainAndPathConf) mustBe
        Headers.of(h0, h1Updated, h2Updated, h3Updated, h4Updated, h5, h6)

      val emptyDomainAndPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain.empty), setNewNameAndValue)
      TransformResponseCookiePlugin.transformCookie(headersIn, emptyDomainAndPathConf) mustBe
        Headers.of(h0, h1Updated, h2, h3, h4, h5, h6)

      val somePathEmptyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), Some(CookieDomain.empty), setNewNameAndValue)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathEmptyDomainConf) mustBe
        Headers.of(h0, h1, h2Updated, h3, h4, h5, h6)

      val somePathAnyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), None, setNewNameAndValue)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathAnyDomainConf) mustBe
        Headers.of(h0, h1, h2Updated, h3, h4Updated, h5, h6)

      val someDomainEmptyPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain("some.domain.com")), setNewNameAndValue)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainEmptyPathConf) mustBe
        Headers.of(h0, h1, h2, h3Updated, h4, h5, h6)

      val someDomainAnyPathConf = TransformResponseCookieConf("cookieOne", None, Some(CookieDomain("some.domain.com")), setNewNameAndValue)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainAnyPathConf) mustBe
        Headers.of(h0, h1, h2, h3Updated, h4Updated, h5, h6)

    }

    "set path of matching cookies only" in {

      val newPath = "/new/path"
      val setNewPath = SetResponseCookie.empty.copy(path = Some(newPath))

      val h0 = "Foo-Header" -> "fooValue"

      val h1 = SET_COOKIE -> "cookieOne=valueOneA"
      val h1Updated = SET_COOKIE -> s"cookieOne=valueOneA; Path=$newPath"

      val h2 = SET_COOKIE -> "cookieOne=valueOneB; Path=/some/path"
      val h2Updated = SET_COOKIE -> s"cookieOne=valueOneB; Path=$newPath"

      val h3 = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com"
      val h3Updated = SET_COOKIE -> s"cookieOne=valueOneC; Path=$newPath; Domain=some.domain.com"

      val h4 = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com"
      val h4Updated = SET_COOKIE -> s"cookieOne=valueOneD; Path=$newPath; Domain=some.domain.com"

      val h5 = SET_COOKIE -> "cookieTwo=valueTwo"
      val h6 = "Bar-Header" -> "barValue"

      val headersIn = Headers.of(h0, h1, h2, h3, h4, h5, h6)


      val anyDomainAndPathConf = TransformResponseCookieConf("cookieOne", None, None, setNewPath)
      TransformResponseCookiePlugin.transformCookie(headersIn, anyDomainAndPathConf) mustBe
        Headers.of(h0, h1Updated, h2Updated, h3Updated, h4Updated, h5, h6)

      val emptyDomainAndPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain.empty), setNewPath)
      TransformResponseCookiePlugin.transformCookie(headersIn, emptyDomainAndPathConf) mustBe
        Headers.of(h0, h1Updated, h2, h3, h4, h5, h6)

      val somePathEmptyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), Some(CookieDomain.empty), setNewPath)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathEmptyDomainConf) mustBe
        Headers.of(h0, h1, h2Updated, h3, h4, h5, h6)

      val somePathAnyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), None, setNewPath)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathAnyDomainConf) mustBe
        Headers.of(h0, h1, h2Updated, h3, h4Updated, h5, h6)

      val someDomainEmptyPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain("some.domain.com")), setNewPath)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainEmptyPathConf) mustBe
        Headers.of(h0, h1, h2, h3Updated, h4, h5, h6)

      val someDomainAnyPathConf = TransformResponseCookieConf("cookieOne", None, Some(CookieDomain("some.domain.com")), setNewPath)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainAnyPathConf) mustBe
        Headers.of(h0, h1, h2, h3Updated, h4Updated, h5, h6)

    }

    "unset path of matching cookies only" in {

      val unsetPath = SetResponseCookie.empty.copy(path = null)

      val h0 = "Foo-Header" -> "fooValue"

      val h1 = SET_COOKIE -> "cookieOne=valueOneA"

      val h2 = SET_COOKIE -> "cookieOne=valueOneB; Path=/some/path"
      val h2Updated = SET_COOKIE -> s"cookieOne=valueOneB"

      val h3 = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com"

      val h4 = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com"
      val h4Updated = SET_COOKIE -> s"cookieOne=valueOneD; Domain=some.domain.com"

      val h5 = SET_COOKIE -> "cookieTwo=valueTwo; Path=/some/path"
      val h6 = "Bar-Header" -> "barValue"

      val headersIn = Headers.of(h0, h1, h2, h3, h4, h5, h6)


      val anyDomainAndPathConf = TransformResponseCookieConf("cookieOne", None, None, unsetPath)
      TransformResponseCookiePlugin.transformCookie(headersIn, anyDomainAndPathConf) mustBe
        Headers.of(h0, h1, h2Updated, h3, h4Updated, h5, h6)

      val emptyDomainAndPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain.empty), unsetPath)
      TransformResponseCookiePlugin.transformCookie(headersIn, emptyDomainAndPathConf) mustBe
        Headers.of(h0, h1, h2, h3, h4, h5, h6)

      val somePathEmptyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), Some(CookieDomain.empty), unsetPath)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathEmptyDomainConf) mustBe
        Headers.of(h0, h1, h2Updated, h3, h4, h5, h6)

      val somePathAnyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), None, unsetPath)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathAnyDomainConf) mustBe
        Headers.of(h0, h1, h2Updated, h3, h4Updated, h5, h6)

      val someDomainEmptyPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain("some.domain.com")), unsetPath)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainEmptyPathConf) mustBe
        Headers.of(h0, h1, h2, h3, h4, h5, h6)

      val someDomainAnyPathConf = TransformResponseCookieConf("cookieOne", None, Some(CookieDomain("some.domain.com")), unsetPath)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainAnyPathConf) mustBe
        Headers.of(h0, h1, h2, h3, h4Updated, h5, h6)

    }

    "set domain of matching cookies only" in {

      val newDomain = "new.domain.com"
      val setNewDomain = SetResponseCookie.empty.copy(domain = Some(newDomain))

      val h0 = "Foo-Header" -> "fooValue"

      val h1 = SET_COOKIE -> "cookieOne=valueOneA"
      val h1Updated = SET_COOKIE -> s"cookieOne=valueOneA; Domain=$newDomain"

      val h2 = SET_COOKIE -> "cookieOne=valueOneB; Path=/some/path"
      val h2Updated = SET_COOKIE -> s"cookieOne=valueOneB; Path=/some/path; Domain=$newDomain"

      val h3 = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com"
      val h3Updated = SET_COOKIE -> s"cookieOne=valueOneC; Domain=$newDomain"

      val h4 = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com"
      val h4Updated = SET_COOKIE -> s"cookieOne=valueOneD; Path=/some/path; Domain=$newDomain"

      val h5 = SET_COOKIE -> "cookieTwo=valueTwo"
      val h6 = "Bar-Header" -> "barValue"

      val headersIn = Headers.of(h0, h1, h2, h3, h4, h5, h6)


      val anyDomainAndPathConf = TransformResponseCookieConf("cookieOne", None, None, setNewDomain)
      TransformResponseCookiePlugin.transformCookie(headersIn, anyDomainAndPathConf) mustBe
        Headers.of(h0, h1Updated, h2Updated, h3Updated, h4Updated, h5, h6)

      val emptyDomainAndPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain.empty), setNewDomain)
      TransformResponseCookiePlugin.transformCookie(headersIn, emptyDomainAndPathConf) mustBe
        Headers.of(h0, h1Updated, h2, h3, h4, h5, h6)

      val somePathEmptyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), Some(CookieDomain.empty), setNewDomain)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathEmptyDomainConf) mustBe
        Headers.of(h0, h1, h2Updated, h3, h4, h5, h6)

      val somePathAnyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), None, setNewDomain)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathAnyDomainConf) mustBe
        Headers.of(h0, h1, h2Updated, h3, h4Updated, h5, h6)

      val someDomainEmptyPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain("some.domain.com")), setNewDomain)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainEmptyPathConf) mustBe
        Headers.of(h0, h1, h2, h3Updated, h4, h5, h6)

      val someDomainAnyPathConf = TransformResponseCookieConf("cookieOne", None, Some(CookieDomain("some.domain.com")), setNewDomain)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainAnyPathConf) mustBe
        Headers.of(h0, h1, h2, h3Updated, h4Updated, h5, h6)

    }

    "unset domain of matching cookies only" in {

      val unsetDomain = SetResponseCookie.empty.copy(domain = null)

      val h0 = "Foo-Header" -> "fooValue"

      val h1 = SET_COOKIE -> "cookieOne=valueOneA"

      val h2 = SET_COOKIE -> "cookieOne=valueOneB; Path=/some/path"

      val h3 = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com"
      val h3Updated = SET_COOKIE -> s"cookieOne=valueOneC"

      val h4 = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com"
      val h4Updated = SET_COOKIE -> s"cookieOne=valueOneD; Path=/some/path"

      val h5 = SET_COOKIE -> "cookieTwo=valueTwo"
      val h6 = "Bar-Header" -> "barValue"

      val headersIn = Headers.of(h0, h1, h2, h3, h4, h5, h6)


      val anyDomainAndPathConf = TransformResponseCookieConf("cookieOne", None, None, unsetDomain)
      TransformResponseCookiePlugin.transformCookie(headersIn, anyDomainAndPathConf) mustBe
        Headers.of(h0, h1, h2, h3Updated, h4Updated, h5, h6)

      val emptyDomainAndPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain.empty), unsetDomain)
      TransformResponseCookiePlugin.transformCookie(headersIn, emptyDomainAndPathConf) mustBe
        Headers.of(h0, h1, h2, h3, h4, h5, h6)

      val somePathEmptyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), Some(CookieDomain.empty), unsetDomain)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathEmptyDomainConf) mustBe
        Headers.of(h0, h1, h2, h3, h4, h5, h6)

      val somePathAnyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), None, unsetDomain)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathAnyDomainConf) mustBe
        Headers.of(h0, h1, h2, h3, h4Updated, h5, h6)

      val someDomainEmptyPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain("some.domain.com")), unsetDomain)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainEmptyPathConf) mustBe
        Headers.of(h0, h1, h2, h3Updated, h4, h5, h6)

      val someDomainAnyPathConf = TransformResponseCookieConf("cookieOne", None, Some(CookieDomain("some.domain.com")), unsetDomain)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainAnyPathConf) mustBe
        Headers.of(h0, h1, h2, h3Updated, h4Updated, h5, h6)

    }

    "set Secure attribute of matching cookies only" in {

      val maxAge = 123456L

      val h0 = "Foo-Header" -> "fooValue"
      val h1 = SET_COOKIE -> "cookieOne=valueOneA; HTTPOnly; SameSite=Lax"
      val h2 = SET_COOKIE -> s"cookieOne=valueOneB; Max-Age=$maxAge; Path=/some/path"
      val h3 = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com"
      val h4 = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com"
      val h5 = SET_COOKIE -> "cookieTwo=valueTwo"
      val h6 = "Bar-Header" -> "barValue"

      val headersIn = Headers.of(h0, h1, h2, h3, h4, h5, h6)

      val setSecure = SetResponseCookie.empty.copy(secure = Some(true))
      val h1Secure = SET_COOKIE -> "cookieOne=valueOneA; Secure; HTTPOnly; SameSite=Lax"
      val h2Secure = SET_COOKIE -> s"cookieOne=valueOneB; Max-Age=$maxAge; Expires=${calcExpires(maxAge)}; Path=/some/path; Secure"
      val h3Secure = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com; Secure"
      val h4Secure = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com; Secure"

      val anyDomainAndPathConf = TransformResponseCookieConf("cookieOne", None, None, setSecure)
      TransformResponseCookiePlugin.transformCookie(headersIn, anyDomainAndPathConf) mustBe
        Headers.of(h0, h1Secure, h2Secure, h3Secure, h4Secure, h5, h6)

      val emptyDomainAndPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain.empty), setSecure)
      TransformResponseCookiePlugin.transformCookie(headersIn, emptyDomainAndPathConf) mustBe
        Headers.of(h0, h1Secure, h2, h3, h4, h5, h6)

      val somePathEmptyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), Some(CookieDomain.empty), setSecure)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathEmptyDomainConf) mustBe
        Headers.of(h0, h1, h2Secure, h3, h4, h5, h6)

      val somePathAnyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), None, setSecure)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathAnyDomainConf) mustBe
        Headers.of(h0, h1, h2Secure, h3, h4Secure, h5, h6)

      val someDomainEmptyPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain("some.domain.com")), setSecure)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainEmptyPathConf) mustBe
        Headers.of(h0, h1, h2, h3Secure, h4, h5, h6)

      val someDomainAnyPathConf = TransformResponseCookieConf("cookieOne", None, Some(CookieDomain("some.domain.com")), setSecure)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainAnyPathConf) mustBe
        Headers.of(h0, h1, h2, h3Secure, h4Secure, h5, h6)

    }

    "unset Secure attribute of matching cookies only" in {

      val maxAge = 123456L

      val h0 = "Foo-Header" -> "fooValue"
      val h1 = SET_COOKIE -> "cookieOne=valueOneA; Secure; HTTPOnly; SameSite=Lax;"
      val h2 = SET_COOKIE -> s"cookieOne=valueOneB; Max-Age=$maxAge; Path=/some/path; Secure"
      val h3 = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com; Secure"
      val h4 = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com; Secure"
      val h5 = SET_COOKIE -> "cookieTwo=valueTwo; Secure"
      val h6 = "Bar-Header" -> "barValue"

      val headersIn = Headers.of(h0, h1, h2, h3, h4, h5, h6)

      val unsetSecure = SetResponseCookie.empty.copy(secure = Some(false))
      val h1Insecure = SET_COOKIE -> "cookieOne=valueOneA; HTTPOnly; SameSite=Lax"
      val h2Insecure = SET_COOKIE -> s"cookieOne=valueOneB; Max-Age=$maxAge; Expires=${calcExpires(maxAge)}; Path=/some/path"
      val h3Insecure = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com"
      val h4Insecure = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com"

      val anyDomainAndPathConf = TransformResponseCookieConf("cookieOne", None, None, unsetSecure)
      TransformResponseCookiePlugin.transformCookie(headersIn, anyDomainAndPathConf) mustBe
        Headers.of(h0, h1Insecure, h2Insecure, h3Insecure, h4Insecure, h5, h6)

      val emptyDomainAndPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain.empty), unsetSecure)
      TransformResponseCookiePlugin.transformCookie(headersIn, emptyDomainAndPathConf) mustBe
        Headers.of(h0, h1Insecure, h2, h3, h4, h5, h6)

      val somePathEmptyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), Some(CookieDomain.empty), unsetSecure)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathEmptyDomainConf) mustBe
        Headers.of(h0, h1, h2Insecure, h3, h4, h5, h6)

      val somePathAnyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), None, unsetSecure)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathAnyDomainConf) mustBe
        Headers.of(h0, h1, h2Insecure, h3, h4Insecure, h5, h6)

      val someDomainEmptyPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain("some.domain.com")), unsetSecure)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainEmptyPathConf) mustBe
        Headers.of(h0, h1, h2, h3Insecure, h4, h5, h6)

      val someDomainAnyPathConf = TransformResponseCookieConf("cookieOne", None, Some(CookieDomain("some.domain.com")), unsetSecure)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainAnyPathConf) mustBe
        Headers.of(h0, h1, h2, h3Insecure, h4Insecure, h5, h6)

    }

    "set HTTPOnly attribute of matching cookies only" in {

      val maxAge = 123456L

      val h0 = "Foo-Header" -> "fooValue"
      val h1 = SET_COOKIE -> "cookieOne=valueOneA; Secure; SameSite=Lax"
      val h2 = SET_COOKIE -> s"cookieOne=valueOneB; Max-Age=$maxAge; Path=/some/path"
      val h3 = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com"
      val h4 = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com"
      val h5 = SET_COOKIE -> "cookieTwo=valueTwo"
      val h6 = "Bar-Header" -> "barValue"

      val headersIn = Headers.of(h0, h1, h2, h3, h4, h5, h6)

      val setSecure = SetResponseCookie.empty.copy(httpOnly = Some(true))
      val h1Secure = SET_COOKIE -> "cookieOne=valueOneA; Secure; HTTPOnly; SameSite=Lax"
      val h2Secure = SET_COOKIE -> s"cookieOne=valueOneB; Max-Age=$maxAge; Expires=${calcExpires(maxAge)}; Path=/some/path; HTTPOnly"
      val h3Secure = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com; HTTPOnly"
      val h4Secure = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com; HTTPOnly"

      val anyDomainAndPathConf = TransformResponseCookieConf("cookieOne", None, None, setSecure)
      TransformResponseCookiePlugin.transformCookie(headersIn, anyDomainAndPathConf) mustBe
        Headers.of(h0, h1Secure, h2Secure, h3Secure, h4Secure, h5, h6)

      val emptyDomainAndPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain.empty), setSecure)
      TransformResponseCookiePlugin.transformCookie(headersIn, emptyDomainAndPathConf) mustBe
        Headers.of(h0, h1Secure, h2, h3, h4, h5, h6)

      val somePathEmptyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), Some(CookieDomain.empty), setSecure)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathEmptyDomainConf) mustBe
        Headers.of(h0, h1, h2Secure, h3, h4, h5, h6)

      val somePathAnyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), None, setSecure)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathAnyDomainConf) mustBe
        Headers.of(h0, h1, h2Secure, h3, h4Secure, h5, h6)

      val someDomainEmptyPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain("some.domain.com")), setSecure)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainEmptyPathConf) mustBe
        Headers.of(h0, h1, h2, h3Secure, h4, h5, h6)

      val someDomainAnyPathConf = TransformResponseCookieConf("cookieOne", None, Some(CookieDomain("some.domain.com")), setSecure)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainAnyPathConf) mustBe
        Headers.of(h0, h1, h2, h3Secure, h4Secure, h5, h6)

    }

    "unset HTTPOnly attribute of matching cookies only" in {

      val maxAge = 123456L

      val h0 = "Foo-Header" -> "fooValue"
      val h1 = SET_COOKIE -> "cookieOne=valueOneA; Secure; HTTPOnly; SameSite=Lax;"
      val h2 = SET_COOKIE -> s"cookieOne=valueOneB; Max-Age=$maxAge; Path=/some/path; HTTPOnly"
      val h3 = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com; HTTPOnly"
      val h4 = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com; HTTPOnly"
      val h5 = SET_COOKIE -> "cookieTwo=valueTwo; HTTPOnly"
      val h6 = "Bar-Header" -> "barValue"

      val headersIn = Headers.of(h0, h1, h2, h3, h4, h5, h6)

      val unsetHttpOnly = SetResponseCookie.empty.copy(httpOnly = Some(false))
      val h1NoHttpOnly = SET_COOKIE -> "cookieOne=valueOneA; Secure; SameSite=Lax"
      val h2NoHttpOnly = SET_COOKIE -> s"cookieOne=valueOneB; Max-Age=$maxAge; Expires=${calcExpires(maxAge)}; Path=/some/path"
      val h3NoHttpOnly = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com"
      val h4NoHttpOnly = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com"

      val anyDomainAndPathConf = TransformResponseCookieConf("cookieOne", None, None, unsetHttpOnly)
      TransformResponseCookiePlugin.transformCookie(headersIn, anyDomainAndPathConf) mustBe
        Headers.of(h0, h1NoHttpOnly, h2NoHttpOnly, h3NoHttpOnly, h4NoHttpOnly, h5, h6)

      val emptyDomainAndPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain.empty), unsetHttpOnly)
      TransformResponseCookiePlugin.transformCookie(headersIn, emptyDomainAndPathConf) mustBe
        Headers.of(h0, h1NoHttpOnly, h2, h3, h4, h5, h6)

      val somePathEmptyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), Some(CookieDomain.empty), unsetHttpOnly)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathEmptyDomainConf) mustBe
        Headers.of(h0, h1, h2NoHttpOnly, h3, h4, h5, h6)

      val somePathAnyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), None, unsetHttpOnly)
      TransformResponseCookiePlugin.transformCookie(headersIn, somePathAnyDomainConf) mustBe
        Headers.of(h0, h1, h2NoHttpOnly, h3, h4NoHttpOnly, h5, h6)

      val someDomainEmptyPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain("some.domain.com")), unsetHttpOnly)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainEmptyPathConf) mustBe
        Headers.of(h0, h1, h2, h3NoHttpOnly, h4, h5, h6)

      val someDomainAnyPathConf = TransformResponseCookieConf("cookieOne", None, Some(CookieDomain("some.domain.com")), unsetHttpOnly)
      TransformResponseCookiePlugin.transformCookie(headersIn, someDomainAnyPathConf) mustBe
        Headers.of(h0, h1, h2, h3NoHttpOnly, h4NoHttpOnly, h5, h6)

    }

  }

  "set SameSite attribute of matching cookies only" in {

    val maxAge = 123456L

    val h0 = "Foo-Header" -> "fooValue"
    val h1 = SET_COOKIE -> "cookieOne=valueOneA; Secure; HTTPOnly; SameSite=Lax"
    val h2 = SET_COOKIE -> s"cookieOne=valueOneB; Max-Age=$maxAge; Path=/some/path"
    val h3 = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com; SameSite=None"
    val h4 = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com"
    val h5 = SET_COOKIE -> "cookieTwo=valueTwo"
    val h6 = "Bar-Header" -> "barValue"

    val headersIn = Headers.of(h0, h1, h2, h3, h4, h5, h6)

    val setSameSiteStrict = SetResponseCookie.empty.copy(sameSite = Some(SameSite.Strict))
    val h1Updated = SET_COOKIE -> "cookieOne=valueOneA; Secure; HTTPOnly; SameSite=Strict"
    val h2Updated = SET_COOKIE -> s"cookieOne=valueOneB; Max-Age=$maxAge; Expires=${calcExpires(maxAge)}; Path=/some/path; SameSite=Strict"
    val h3Updated = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com; SameSite=Strict"
    val h4Updated = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com; SameSite=Strict"

    val anyDomainAndPathConf = TransformResponseCookieConf("cookieOne", None, None, setSameSiteStrict)
    TransformResponseCookiePlugin.transformCookie(headersIn, anyDomainAndPathConf) mustBe
      Headers.of(h0, h1Updated, h2Updated, h3Updated, h4Updated, h5, h6)

    val emptyDomainAndPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain.empty), setSameSiteStrict)
    TransformResponseCookiePlugin.transformCookie(headersIn, emptyDomainAndPathConf) mustBe
      Headers.of(h0, h1Updated, h2, h3, h4, h5, h6)

    val somePathEmptyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), Some(CookieDomain.empty), setSameSiteStrict)
    TransformResponseCookiePlugin.transformCookie(headersIn, somePathEmptyDomainConf) mustBe
      Headers.of(h0, h1, h2Updated, h3, h4, h5, h6)

    val somePathAnyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), None, setSameSiteStrict)
    TransformResponseCookiePlugin.transformCookie(headersIn, somePathAnyDomainConf) mustBe
      Headers.of(h0, h1, h2Updated, h3, h4Updated, h5, h6)

    val someDomainEmptyPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain("some.domain.com")), setSameSiteStrict)
    TransformResponseCookiePlugin.transformCookie(headersIn, someDomainEmptyPathConf) mustBe
      Headers.of(h0, h1, h2, h3Updated, h4, h5, h6)

    val someDomainAnyPathConf = TransformResponseCookieConf("cookieOne", None, Some(CookieDomain("some.domain.com")), setSameSiteStrict)
    TransformResponseCookiePlugin.transformCookie(headersIn, someDomainAnyPathConf) mustBe
      Headers.of(h0, h1, h2, h3Updated, h4Updated, h5, h6)

  }

  "unset SameSite attribute of matching cookies only" in {

    val maxAge = 123456L

    val h0 = "Foo-Header" -> "fooValue"
    val h1 = SET_COOKIE -> "cookieOne=valueOneA; Secure; HTTPOnly; SameSite=Lax"
    val h2 = SET_COOKIE -> s"cookieOne=valueOneB; Max-Age=$maxAge; Path=/some/path; SameSite=Strict"
    val h3 = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com; SameSite=None"
    val h4 = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com; SameSite=Lax"
    val h5 = SET_COOKIE -> "cookieTwo=valueTwo; SameSite=Lax"
    val h6 = "Bar-Header" -> "barValue"

    val headersIn = Headers.of(h0, h1, h2, h3, h4, h5, h6)

    val unsetSameSite = SetResponseCookie.empty.copy(sameSite = null)
    val h1Updated = SET_COOKIE -> "cookieOne=valueOneA; Secure; HTTPOnly"
    val h2Updated = SET_COOKIE -> s"cookieOne=valueOneB; Max-Age=$maxAge; Expires=${calcExpires(maxAge)}; Path=/some/path"
    val h3Updated = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com"
    val h4Updated = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com"

    val anyDomainAndPathConf = TransformResponseCookieConf("cookieOne", None, None, unsetSameSite)
    TransformResponseCookiePlugin.transformCookie(headersIn, anyDomainAndPathConf) mustBe
      Headers.of(h0, h1Updated, h2Updated, h3Updated, h4Updated, h5, h6)

    val emptyDomainAndPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain.empty), unsetSameSite)
    TransformResponseCookiePlugin.transformCookie(headersIn, emptyDomainAndPathConf) mustBe
      Headers.of(h0, h1Updated, h2, h3, h4, h5, h6)

    val somePathEmptyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), Some(CookieDomain.empty), unsetSameSite)
    TransformResponseCookiePlugin.transformCookie(headersIn, somePathEmptyDomainConf) mustBe
      Headers.of(h0, h1, h2Updated, h3, h4, h5, h6)

    val somePathAnyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), None, unsetSameSite)
    TransformResponseCookiePlugin.transformCookie(headersIn, somePathAnyDomainConf) mustBe
      Headers.of(h0, h1, h2Updated, h3, h4Updated, h5, h6)

    val someDomainEmptyPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain("some.domain.com")), unsetSameSite)
    TransformResponseCookiePlugin.transformCookie(headersIn, someDomainEmptyPathConf) mustBe
      Headers.of(h0, h1, h2, h3Updated, h4, h5, h6)

    val someDomainAnyPathConf = TransformResponseCookieConf("cookieOne", None, Some(CookieDomain("some.domain.com")), unsetSameSite)
    TransformResponseCookiePlugin.transformCookie(headersIn, someDomainAnyPathConf) mustBe
      Headers.of(h0, h1, h2, h3Updated, h4Updated, h5, h6)

  }

  "set Max-Age attribute of matching cookies only" in {

    val h0 = "Foo-Header" -> "fooValue"
    val h1 = SET_COOKIE -> "cookieOne=valueOneA; Secure; HTTPOnly; SameSite=Lax"
    val h2 = SET_COOKIE -> s"cookieOne=valueOneB; Max-Age=67890; Path=/some/path"
    val h3 = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com; SameSite=None"
    val h4 = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com"
    val h5 = SET_COOKIE -> "cookieTwo=valueTwo"
    val h6 = "Bar-Header" -> "barValue"

    val maxAge = 12345L
    val headersIn = Headers.of(h0, h1, h2, h3, h4, h5, h6)

    val setMaxAge = SetResponseCookie.empty.copy(maxAge = Some(maxAge))
    val h1Updated = SET_COOKIE -> s"cookieOne=valueOneA; Max-Age=$maxAge; Expires=${calcExpires(maxAge)}; Secure; HTTPOnly; SameSite=Lax"
    val h2Updated = SET_COOKIE -> s"cookieOne=valueOneB; Max-Age=$maxAge; Expires=${calcExpires(maxAge)}; Path=/some/path"
    val h3Updated = SET_COOKIE -> s"cookieOne=valueOneC; Max-Age=$maxAge; Expires=${calcExpires(maxAge)}; Domain=some.domain.com; SameSite=None"
    val h4Updated = SET_COOKIE -> s"cookieOne=valueOneD; Max-Age=$maxAge; Expires=${calcExpires(maxAge)}; Path=/some/path; Domain=some.domain.com"

    val anyDomainAndPathConf = TransformResponseCookieConf("cookieOne", None, None, setMaxAge)
    TransformResponseCookiePlugin.transformCookie(headersIn, anyDomainAndPathConf) mustBe
      Headers.of(h0, h1Updated, h2Updated, h3Updated, h4Updated, h5, h6)

    val emptyDomainAndPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain.empty), setMaxAge)
    TransformResponseCookiePlugin.transformCookie(headersIn, emptyDomainAndPathConf) mustBe
      Headers.of(h0, h1Updated, h2, h3, h4, h5, h6)

    val somePathEmptyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), Some(CookieDomain.empty), setMaxAge)
    TransformResponseCookiePlugin.transformCookie(headersIn, somePathEmptyDomainConf) mustBe
      Headers.of(h0, h1, h2Updated, h3, h4, h5, h6)

    val somePathAnyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), None, setMaxAge)
    TransformResponseCookiePlugin.transformCookie(headersIn, somePathAnyDomainConf) mustBe
      Headers.of(h0, h1, h2Updated, h3, h4Updated, h5, h6)

    val someDomainEmptyPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain("some.domain.com")), setMaxAge)
    TransformResponseCookiePlugin.transformCookie(headersIn, someDomainEmptyPathConf) mustBe
      Headers.of(h0, h1, h2, h3Updated, h4, h5, h6)

    val someDomainAnyPathConf = TransformResponseCookieConf("cookieOne", None, Some(CookieDomain("some.domain.com")), setMaxAge)
    TransformResponseCookiePlugin.transformCookie(headersIn, someDomainAnyPathConf) mustBe
      Headers.of(h0, h1, h2, h3Updated, h4Updated, h5, h6)

  }

  "unset Max-Age attribute of matching cookies only" in {

    val maxAge = 12345L
    val expires = calcExpires(maxAge)
    val h0 = "Foo-Header" -> "fooValue"
    val h1 = SET_COOKIE -> s"cookieOne=valueOneA; Max-Age=$maxAge; Expires=$expires; Secure; HTTPOnly; SameSite=Lax"
    val h2 = SET_COOKIE -> s"cookieOne=valueOneB; Max-Age=$maxAge; Expires=$expires; Path=/some/path"
    val h3 = SET_COOKIE -> s"cookieOne=valueOneC; Max-Age=$maxAge; Expires=$expires; Domain=some.domain.com; SameSite=None"
    val h4 = SET_COOKIE -> s"cookieOne=valueOneD; Max-Age=$maxAge; Expires=$expires; Path=/some/path; Domain=some.domain.com"
    val h5 = SET_COOKIE -> "cookieTwo=valueTwo"
    val h6 = "Bar-Header" -> "barValue"

    val headersIn = Headers.of(h0, h1, h2, h3, h4, h5, h6)

    val unsetMaxAge = SetResponseCookie.empty.copy(maxAge = null)
    val h1Updated = SET_COOKIE -> "cookieOne=valueOneA; Secure; HTTPOnly; SameSite=Lax"
    val h2Updated = SET_COOKIE -> s"cookieOne=valueOneB; Path=/some/path"
    val h3Updated = SET_COOKIE -> "cookieOne=valueOneC; Domain=some.domain.com; SameSite=None"
    val h4Updated = SET_COOKIE -> "cookieOne=valueOneD; Path=/some/path; Domain=some.domain.com"

    val anyDomainAndPathConf = TransformResponseCookieConf("cookieOne", None, None, unsetMaxAge)
    TransformResponseCookiePlugin.transformCookie(headersIn, anyDomainAndPathConf) mustBe
      Headers.of(h0, h1Updated, h2Updated, h3Updated, h4Updated, h5, h6)

    val emptyDomainAndPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain.empty), unsetMaxAge)
    TransformResponseCookiePlugin.transformCookie(headersIn, emptyDomainAndPathConf) mustBe
      Headers.of(h0, h1Updated, h2, h3, h4, h5, h6)

    val somePathEmptyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), Some(CookieDomain.empty), unsetMaxAge)
    TransformResponseCookiePlugin.transformCookie(headersIn, somePathEmptyDomainConf) mustBe
      Headers.of(h0, h1, h2Updated, h3, h4, h5, h6)

    val somePathAnyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), None, unsetMaxAge)
    TransformResponseCookiePlugin.transformCookie(headersIn, somePathAnyDomainConf) mustBe
      Headers.of(h0, h1, h2Updated, h3, h4Updated, h5, h6)

    val someDomainEmptyPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain("some.domain.com")), unsetMaxAge)
    TransformResponseCookiePlugin.transformCookie(headersIn, someDomainEmptyPathConf) mustBe
      Headers.of(h0, h1, h2, h3Updated, h4, h5, h6)

    val someDomainAnyPathConf = TransformResponseCookieConf("cookieOne", None, Some(CookieDomain("some.domain.com")), unsetMaxAge)
    TransformResponseCookiePlugin.transformCookie(headersIn, someDomainAnyPathConf) mustBe
      Headers.of(h0, h1, h2, h3Updated, h4Updated, h5, h6)

  }

  "not change or reorder other headers or non-matching cookies" in {

    val headersIn = Headers.of(
      "Foo-Header" -> "fooValue",
      SET_COOKIE -> "cookieOne=valueOne; Domain=some.domain.com",
      SET_COOKIE -> "cookieOne=valueOne; Path=/some/path",
      SET_COOKIE -> "cookieOne=valueOne; Domain=some.domain.com; Path=/some/path",
      SET_COOKIE -> "cookieTwo=valueTwo",
      "Bar-Header" -> "barValue"
    )

    val emptyDomainAndPathConf = TransformResponseCookieConf("cookieOne", Some(CookiePath.empty), Some(CookieDomain.empty), SetResponseCookie.empty.copy(value=Some("newValue")))
    headersIn mustBe TransformResponseCookiePlugin.transformCookie(headersIn, emptyDomainAndPathConf)

    val differentDomainAnyPathConf = TransformResponseCookieConf("cookieOne", None, Some(CookieDomain("different.domain.com")), SetResponseCookie.empty.copy(value=Some("newValue")))
    headersIn mustBe TransformResponseCookiePlugin.transformCookie(headersIn, differentDomainAnyPathConf)

    val differentPathAnyDomainConf = TransformResponseCookieConf("cookieOne", Some(CookiePath("/different/path")), None, SetResponseCookie.empty.copy(value=Some("newValue")))
    headersIn mustBe TransformResponseCookiePlugin.transformCookie(headersIn, differentPathAnyDomainConf)

    val sameDomainDifferentPath = TransformResponseCookieConf("cookieOne", Some(CookiePath("/different/path")), Some(CookieDomain("some.domain.com")), SetResponseCookie.empty.copy(value=Some("newValue")))
    headersIn mustBe TransformResponseCookiePlugin.transformCookie(headersIn, sameDomainDifferentPath)

    val samePathDifferentDomain = TransformResponseCookieConf("cookieOne", Some(CookiePath("/some/path")), Some(CookieDomain("different.domain.com")), SetResponseCookie.empty.copy(value=Some("newValue")))
    headersIn mustBe TransformResponseCookiePlugin.transformCookie(headersIn, samePathDifferentDomain)
  }

}
