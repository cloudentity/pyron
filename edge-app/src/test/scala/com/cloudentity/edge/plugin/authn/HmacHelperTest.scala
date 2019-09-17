package com.cloudentity.edge.plugin.authn

import java.time.Instant._
import java.time.ZoneId
import java.time.format.DateTimeFormatter._

import com.cloudentity.edge.api.Responses.{Error, ErrorBody}
import com.cloudentity.edge.plugin.impl.authn.HmacHelper
import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatest.junit.JUnitRunner
import scalaz.{-\/, \/-}

@RunWith(classOf[JUnitRunner])
class HmacHelperTest extends WordSpec with MustMatchers {
  val dateFormatWithTimeZone = "EEE, d MMM yyyy HH:mm:ss Z"
  val dateStringWithTimeZoneNewYork = ofPattern(dateFormatWithTimeZone).format(now().atZone(ZoneId.of("America/New_York")))
  val helperWithDateWithTimeZone = new HmacHelper(dateFormatWithTimeZone, 10, "orchis-hmac")

  val dateFormatWithoutTimeZone = "EEE, d MMM yyyy HH:mm:ss"
  val dateStringWithoutTimeZoneNewYork = ofPattern(dateFormatWithoutTimeZone).format(now().atZone(ZoneId.of("America/New_York")))
  val dateStringWithoutTimeZonePoland = ofPattern(dateFormatWithoutTimeZone).format(now().atZone(ZoneId.of("Poland")))
  val helperWithDateWithoutTimeZone = new HmacHelper(dateFormatWithoutTimeZone, 10, "orchis-hmac")

  "HmacHelper" should {
    "correctly support incoming offset in date string" in {
      helperWithDateWithTimeZone.validateDate(dateStringWithTimeZoneNewYork) mustBe \/-(())
    }

    "fallback to UTC if date format does not contain offset placeholder" in {
      helperWithDateWithoutTimeZone.validateDate(dateStringWithoutTimeZoneNewYork) mustBe -\/(Error(401, ErrorBody("Authentication.RequestOutdated", "The request was outdated.")))
      helperWithDateWithoutTimeZone.validateDate(dateStringWithoutTimeZonePoland) mustBe \/-(())
    }
  }

}
