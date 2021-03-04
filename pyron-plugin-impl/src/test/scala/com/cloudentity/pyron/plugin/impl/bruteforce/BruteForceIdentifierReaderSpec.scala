package com.cloudentity.pyron.plugin.impl.bruteforce

import com.cloudentity.pyron.test.TestRequestResponseCtx
import io.circe.Json._
import io.vertx.core.buffer.Buffer
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class BruteForceIdentifierReaderSpec extends WordSpec with MustMatchers with TestRequestResponseCtx {
  "IdentifierReader" should {
    "read id from header" in {
      BruteForceIdentifierReader.read(
        emptyRequestCtx.modifyRequest(_.withHeader("ID", "x")),
        DeprecatedIdentifierSource(HeaderIdentifier, "ID")
      ) mustBe Some("x")
    }

    "return None if id missing in header" in {
      BruteForceIdentifierReader.read(emptyRequestCtx, DeprecatedIdentifierSource(HeaderIdentifier, "ID")) mustBe None
    }

    "read id from body at top level" in {
      val body = obj("id" -> fromString("x")).noSpaces
      BruteForceIdentifierReader.read(
        emptyRequestCtx.modifyRequest(_.copy(bodyOpt = Some(Buffer.buffer(body)))),
        DeprecatedIdentifierSource(BodyIdentifier, "id")
      ) mustBe Some("x")
    }

    "read id from body at deep level" in {
      val body = obj("user" -> obj("credentials" -> obj("username" -> fromString("x")))).noSpaces
      BruteForceIdentifierReader.read(
        emptyRequestCtx.modifyRequest(_.copy(bodyOpt = Some(Buffer.buffer(body)))),
        DeprecatedIdentifierSource(BodyIdentifier, "user.credentials.username")
      ) mustBe Some("x")
    }

    "return None if id missing in body" in {
      BruteForceIdentifierReader.read(emptyRequestCtx, DeprecatedIdentifierSource(BodyIdentifier, "id")) mustBe None
    }
  }
}
