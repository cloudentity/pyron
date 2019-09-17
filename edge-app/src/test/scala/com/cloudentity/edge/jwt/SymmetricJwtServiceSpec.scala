package com.cloudentity.edge.jwt

import io.circe.generic.auto._
import io.circe.parser._
import com.cloudentity.edge.jwt.impl.{SymmetricJwtConf, SymmetricJwtService}
import com.cloudentity.edge.util.{FutureUtils, JwtUtils}
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import io.vertx.core.json.JsonObject
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class SymmetricJwtServiceSpec extends WordSpec with MustMatchers with JwtUtils with FutureUtils {
  case class JwtContent(iss: String, exp: Long, iat: Long)

  implicit val ec: VertxExecutionContext = null

  "SymmetricJwtService" should {
    "generate empty jwt that can be decoded properly" in {
      //given
      val secret = "secret"
      val issuer = "issuer"
      val durationInSeconds: Long = 10

      val conf = SymmetricJwtConf(secret, issuer, java.time.Duration.ofSeconds(durationInSeconds), None, None)
      val service = SymmetricJwtService.build(conf)

      //when
      val jwt: String = await(service.emptySigned)._2
      val decoded = await(service.parse(jwt))
      val content: JwtContent = decode[JwtContent](decoded.claims.noSpaces).toTry.get

      //then
      content.iss must be(issuer)
      (content.exp - content.iat - 5L) must be(durationInSeconds)
    }

    "generate jwt with custom claims" in {
      // given
      val secret = "secret"
      val issuer = "issuer"
      val durationInSeconds: Long = 10

      val token = "token-123"
      val conf = SymmetricJwtConf(secret, issuer, java.time.Duration.ofSeconds(durationInSeconds), None, None)
      val service = SymmetricJwtService.build(conf)
      val jwt = await(service.empty()).put("token", Some(token))

      // when
      val encoded = await(service.sign(jwt))
      val decodedContent = new JsonObject(await(service.parse(encoded)).claims.noSpaces)

      // then
      decodedContent.getJsonObject("content").getString("token") must be (token)
    }
  }
}
