package com.cloudentity.edge.jwt

import io.circe.{Json, JsonObject}
import io.circe.parser._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class JwtMappingSpec extends WordSpec with MustMatchers {
  "JwtMapping" should {
    "remap json" in {
      val claims: Json = parse("""
        {
          "token": "1234",
          "session": {"uuid": "a", "deviceUuid": "b", "active": true, "entitlements": ["X", "Y"]},
          "authnMethod": "sso",
          "apps": [{"app": "1"}, {"app": "2"}],
          "user": {"uuid": "a", "name": "andrzej"},
          "device": {"uuid": "b", "name": "android"}
          }
      """).right.get

      val mapping: Json = parse("""
        {
          "usr": "${user}",
          "device": "${invalid}",
          "name": "token",
          "level": "${session.level}",
          "ents": "${session.entitlements}",
          "appId": "${apps.2.app}",
          "values": ["a", "b"],
          "sso": {
            "active": "${session.active}"
          }
        }
      """).right.get

      val defaults: JsonObject = JsonObject.fromMap(Map(
        "session.level" -> parse("15").right.get
      ))

      val expected: Json = parse(
        """
          {
            "sso": {
              "active": true
            },
            "name": "token",
            "ents": ["X", "Y"],
            "appId": "2",
            "values": ["a", "b"],
            "level": 15,
            "usr": {
              "uuid": "a",
              "name": "andrzej"
            }
          }
        """).right.get

      val result = JwtMapping.updateWithRefs(mapping, claims, defaults)

      result must be(expected)
    }
  }
}
