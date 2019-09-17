package com.cloudentity.edge.plugin.authn

import java.time.format.DateTimeFormatter
import java.time.{Duration, Instant, ZoneId}

import com.cloudentity.edge.api.Responses.Errors
import com.cloudentity.edge.plugin.impl.authn.HmacHelper
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

import scalaz.{-\/, \/-}

@RunWith(classOf[JUnitRunner])
class HmacTest extends WordSpec with MustMatchers {

  val hmac: HmacHelper = new HmacHelper("EEE, d MMM yyyy HH:mm:ss Z", 10, "orchis-hmac")
  val formatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z").withZone(ZoneId.of("UTC"))
  val now = formatter.format(Instant.now())

  val url = "http://iiam.example.com:8080/user?realm=ovl&limit=10"
  val signature = "frJIUN8DYpKDtOLCwo//yllqDzg="
  val typ = "orchis-hmac"
  val uuid = "1234-1234-1234-1234"
  val apiKey = "asd0dcfjas89cja9s8du1"
  val body = "{\"identifer\": \"abc@abc.pl\", \"password\": \"pass1234\"}"

  val invalidAuthHeader = Errors.invalidRequest.copy(body = Errors.invalidRequest.body.copy(message = "Invalid authorization header"))
  val invalidUrl = Errors.invalidRequest.copy(body = Errors.invalidRequest.body.copy(message = "Invalid URL"))

  "Date validation" should {
    "pass when date matches given timeout" in {
      hmac.validateDate(now) match {
        case -\/(err) => fail(s"Date validation should pass: ${err}")
        case \/-(_) => assert(true)
      }
    }
    "fail when date matches given timeout" in {
      hmac.validateDate(formatter.format(Instant.now().minus(Duration.ofMinutes(20)))) match {
        case -\/(err) => err mustBe(Errors.hmacRequestOutdated)
        case \/-(_) => fail(s"Date validation shouldn't pass")
      }
    }
    "fail when date is a garbage" in {
      hmac.validateDate("garbageXxXx") match {
        case -\/(err) => err mustBe(Errors.invalidRequest)
        case \/-(_) => fail(s"Date validation shouldn't pass")
      }
    }
  }

  "Build auth header" should {
    "pass when auth header matches expectation" in {
      hmac.buildAuthorizationHeader(typ, uuid, "signature") mustBe(s"${typ} ${uuid}:signature")
    }
  }

  "Get uuid from auth header" should {
    "pass when uuid is the same as in auth header" in {
      val authHeader = hmac.buildAuthorizationHeader(typ, uuid, "signature")
      hmac.getUUID(authHeader) match {
        case -\/(err) => fail(s"${err.body.message}")
        case \/-(uuid) => uuid mustBe(uuid)
      }
    }
    "fail when auth header is incorrect" in {
      val authHeader = typ
      hmac.getUUID(authHeader) match {
        case -\/(err) => err mustBe(Errors.invalidRequest)
        case \/-(_) => fail("shouldn't pass")
      }

      val authHeader2 = s"${typ} dsdsdsd dssd sds:d:ds"
      hmac.getUUID(authHeader2) match {
        case -\/(err) => err mustBe(Errors.invalidRequest)
        case \/-(_) => fail("shouldn't pass")
      }
    }
  }

  "Get realm or default" should {
    "pass when realm matches expectation" in {
      val authHeader = s"${typ} ${uuid}:signature:internal-applications"
      hmac.getRealmOrDefault(authHeader, "orchis") mustBe("internal-applications")

      val authHeaderWithoutRealm = s"${typ} ${uuid}:signature"
      hmac.getRealmOrDefault(authHeaderWithoutRealm, "orchis") mustBe("orchis")
    }
  }

  "Get signature" should {
    "pass when signature matches expectation" in {
      val authHeader = s"${typ} ${uuid}:signature:internal-applications"
      hmac.getSignature(authHeader) match {
        case -\/(err) => fail(err.body.message)
        case \/-(sign) => sign mustBe("signature")
      }

      val authHeader2 = s"${typ} ${uuid}:"
      hmac.getSignature(authHeader2) match {
        case -\/(err) => err mustBe(Errors.invalidRequest)
        case \/-(_) => fail()
      }
    }
  }

  "Build signature" should {
    "pass if generates unique signatures" in {
      val sign1 = hmac.buildSignature(apiKey.getBytes, "request".getBytes) match {
        case -\/(_) => fail()
        case \/-(sign) => sign
      }

      val sign2 = hmac.buildSignature(apiKey.getBytes, "requezt".getBytes) match {
        case -\/(_) => fail()
        case \/-(sign) => sign
      }

      val sign3 = hmac.buildSignature("asd".getBytes, "request".getBytes) match {
        case -\/(_) => fail()
        case \/-(sign) => sign
      }

      sign1 mustBe "ni55YadPTP0qHRRcwMyPi14/sNydve4Za9pplXicHo0="
      sign2 mustNot be(sign1)
      sign3 mustNot be(sign1)
    }
  }

  "Build request" should {
    val now = Instant.now()

    "pass if request matches" in {
      hmac.buildRequest("POST", body, now, "http://iiam.example.com:8080/user?realm=ovl&limit=10") match {
        case -\/(_) => fail()
        case \/-(req) => req must be("POST\n" +
          s"${hmac.md5(body)}\n" +
          s"${formatter.format(now)}\n" +
          "iiam.example.com:8080/user\n" +
          "limit=10&realm=ovl")
      }
    }

    "fail when url is invalid" in {
      hmac.buildRequest("POST", body, now, "iiam.example.com:8080/user?limit=10&realm=ovl") match {
        case -\/(err) => err mustBe(Errors.invalidRequest)
        case \/-(_) => fail()
      }
    }

    "pass when url is correctly normalized" in {
      hmac.buildRequest("POST", body, now, "http://iiam.example.com:80/user?aaa=xxx&realm=ovl&limit=10") match {
        case -\/(_) => fail()
        case \/-(req) => req must be("POST\n" +
          s"${hmac.md5(body)}\n" +
          s"${formatter.format(now)}\n" +
          "iiam.example.com/user\n" +
          "aaa=xxx&limit=10&realm=ovl")
      }
    }
  }
}