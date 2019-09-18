package com.cloudentity.edge.plugin.impl.bruteforce

import com.cloudentity.edge.plugin.impl.bruteforce.{BodyIdentifier, BruteForceIdentifier, HeaderIdentifier, IdentifierReader}
import io.vertx.core.buffer.Buffer
import org.scalatest.{MustMatchers, WordSpec}
import io.circe._
import io.circe.Json._
import com.cloudentity.edge.test.TestRequestResponseCtx
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class IdentifierReaderSpec extends WordSpec with MustMatchers with TestRequestResponseCtx {
  "IdentifierReader" should {
    "read id from header" in {
      IdentifierReader.read(emptyRequest.withHeader("ID", "x"), BruteForceIdentifier(HeaderIdentifier, "ID")) must be(Some("x"))
    }

    "return None if id missing in header" in {
      IdentifierReader.read(emptyRequest, BruteForceIdentifier(HeaderIdentifier, "ID")) must be(None)
    }

    "read id from body at top level" in {
      val body = obj("id" -> fromString("x")).noSpaces
      IdentifierReader.read(emptyRequest.copy(bodyOpt = Some(Buffer.buffer(body))), BruteForceIdentifier(BodyIdentifier, "id")) must be(Some("x"))
    }

    "read id from body at deep level" in {
      val body = obj("user" -> obj("credentials" -> obj("username" -> fromString("x")))).noSpaces
      IdentifierReader.read(emptyRequest.copy(bodyOpt = Some(Buffer.buffer(body))), BruteForceIdentifier(BodyIdentifier, "user.credentials.username")) must be(Some("x"))
    }

    "return None if id missing in body" in {
      IdentifierReader.read(emptyRequest, BruteForceIdentifier(BodyIdentifier, "id")) must be(None)
    }
  }
}
