package com.cloudentity.pyron.plugin.impl.bruteforce

import com.cloudentity.pyron.test.TestRequestResponseCtx
import io.circe.Json._
import io.vertx.core.buffer.Buffer
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class BruteForceIdentifierReaderSpec extends WordSpec with MustMatchers with TestRequestResponseCtx {
  "IdentifierReader" should {
    "read id from header" in {
      BruteForceIdentifierReader.read(emptyRequest.withHeader("ID", "x"), IdentifierSource(HeaderIdentifier, "ID")) must be(Some("x"))
    }

    "return None if id missing in header" in {
      BruteForceIdentifierReader.read(emptyRequest, IdentifierSource(HeaderIdentifier, "ID")) must be(None)
    }

    "read id from body at top level" in {
      val body = obj("id" -> fromString("x")).noSpaces
      BruteForceIdentifierReader.read(emptyRequest.copy(bodyOpt = Some(Buffer.buffer(body))), IdentifierSource(BodyIdentifier, "id")) must be(Some("x"))
    }

    "read id from body at deep level" in {
      val body = obj("user" -> obj("credentials" -> obj("username" -> fromString("x")))).noSpaces
      BruteForceIdentifierReader.read(emptyRequest.copy(bodyOpt = Some(Buffer.buffer(body))), IdentifierSource(BodyIdentifier, "user.credentials.username")) must be(Some("x"))
    }

    "return None if id missing in body" in {
      BruteForceIdentifierReader.read(emptyRequest, IdentifierSource(BodyIdentifier, "id")) must be(None)
    }
  }
}
