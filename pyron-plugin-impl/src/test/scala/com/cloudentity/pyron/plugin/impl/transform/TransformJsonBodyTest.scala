package com.cloudentity.pyron.plugin.impl.transform

import com.cloudentity.pyron.plugin.impl.transform.TransformJsonBody.{applyBodyTransformations, setJsonBody}
import com.cloudentity.pyron.plugin.util.value._
import com.cloudentity.pyron.test.TestRequestResponseCtx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.{JsonArray, JsonObject}
import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TransformJsonBodyTest extends WordSpec with MustMatchers with TestRequestResponseCtx {

  "TransformJsonBody.setJsonBody" should {

    def emptyBody = new JsonObject()

    "set string value in empty body" in {
      val valAtPath = Map(Path("a") -> Option(StringJsonValue("string")))
      setJsonBody(valAtPath)(emptyBody) mustBe emptyBody.put("a", "string")
    }
    "set string value deep in empty body" in {
      val valAtPath = Map(Path("a", "b") -> Option(StringJsonValue("string")))
      setJsonBody(valAtPath)(emptyBody) mustBe emptyBody.put("a", emptyBody.put("b", "string"))
    }
    "set json object value in empty body" in {
      val obj = new JsonObject().put("x", "y")
      val valAtPath = Map(Path("a") -> Option(ObjectJsonValue(obj.copy())))
      setJsonBody(valAtPath)(emptyBody) mustBe emptyBody.put("a", obj.copy())
    }
    "set json array value in empty body" in {
      val arr = new JsonArray().add("x")
      val valAtPath = Map(Path("a") -> Option(ArrayJsonValue(arr.copy())))
      setJsonBody(valAtPath)(emptyBody) mustBe emptyBody.put("a", arr.copy())
    }
    "set null value in empty body" in {
      val valAtPath = Map(Path("a") -> Option(NullJsonValue))
      setJsonBody(valAtPath)(emptyBody) mustBe emptyBody.put("a", null.asInstanceOf[String])
    }

    def shallowBody = new JsonObject().put("x", "value")

    "set string value in shallow" in {
      val valAtPath = Map(Path("a") -> Option(StringJsonValue("string")))
      setJsonBody(valAtPath)(shallowBody) mustBe shallowBody.put("a", "string")
    }
    "set string value deep in shallow" in {
      val valAtPath = Map(Path("a", "b") -> Option(StringJsonValue("string")))
      setJsonBody(valAtPath)(shallowBody) mustBe shallowBody.put("a", new JsonObject().put("b", "string"))
    }
    "set json object value in shallow" in {
      val obj = new JsonObject().put("x", "y")
      val valAtPath = Map(Path("a") -> Option(ObjectJsonValue(obj.copy())))
      setJsonBody(valAtPath)(shallowBody) mustBe shallowBody.put("a", obj.copy())
    }
    "set json array value in shallow" in {
      val arr = new JsonArray().add("x")
      val valAtPath = Map(Path("a") -> Option(ArrayJsonValue(arr.copy())))
      setJsonBody(valAtPath)(shallowBody) mustBe shallowBody.put("a", arr.copy())
    }
    "set null value in shallow" in {
      val valAtPath = Map(Path("a") -> Option(NullJsonValue))
      setJsonBody(valAtPath)(shallowBody) mustBe shallowBody.put("a", null.asInstanceOf[String])
    }

    "overwrite with string value in shallow" in {
      val valAtPath = Map(Path("x") -> Option(StringJsonValue("string")))
      setJsonBody(valAtPath)(shallowBody) mustBe emptyBody.put("x", "string")
    }
    "overwrite with json object value in shallow" in {
      val obj = new JsonObject().put("u", "v")
      val valAtPath = Map(Path("x") -> Option(ObjectJsonValue(obj.copy())))
      setJsonBody(valAtPath)(shallowBody) mustBe emptyBody.put("x", obj.copy())
    }
    "overwrite with json array value in shallow" in {
      val arr = new JsonArray().add("x")
      val valAtPath = Map(Path("x") -> Option(ArrayJsonValue(arr.copy())))
      setJsonBody(valAtPath)(shallowBody) mustBe emptyBody.put("x", arr.copy())
    }
    "overwrite with null value in shallow" in {
      val valAtPath = Map(Path("x") -> Option(NullJsonValue))
      setJsonBody(valAtPath)(shallowBody) mustBe emptyBody.put("x", null.asInstanceOf[String])
    }
    "overwrite with null in shallow if reference missing" in {
      val valAtPath = Map(Path("x") -> None)
      setJsonBody(valAtPath)(shallowBody) mustBe emptyBody.put("x", null.asInstanceOf[String])
    }

    def complexBody = new JsonObject(""" { "x": "value", "y" : { "z": "value" } } """)

    "overwrite with string value in complex body" in {
      val valAtPath = Map(Path("y", "z") -> Option(StringJsonValue("string")))
      setJsonBody(valAtPath)(complexBody) mustBe
        new JsonObject(""" { "x": "value", "y" : { "z": "string" } } """)
    }
    "overwrite with json object value in complex body" in {
      val obj = new JsonObject().put("u", "v")
      val valAtPath = Map(Path("y", "z") -> Option(ObjectJsonValue(obj)))
      setJsonBody(valAtPath)(complexBody) mustBe
        new JsonObject(""" { "x": "value", "y" : { "z": { "u" : "v" } } } """)
    }
    "overwrite with json array value in complex body" in {
      val arr = new JsonArray().add("u")
      val valAtPath = Map(Path("y", "z") -> Option(ArrayJsonValue(arr)))
      setJsonBody(valAtPath)(complexBody) mustBe
        new JsonObject(""" { "x": "value", "y" : { "z": [ "u" ] } } """)
    }
    "overwrite with null value in complex body" in {
      val valAtPath = Map(Path("y", "z") -> Option(NullJsonValue))
      setJsonBody(valAtPath)(complexBody) mustBe
        new JsonObject(""" { "x": "value", "y" : { "z": null } } """)
    }
    "overwrite with null in complex deep if reference missing" in {
      val valAtPath = Map(Path("y", "z") -> None)
      setJsonBody(valAtPath)(complexBody) mustBe
        new JsonObject(""" { "x": "value", "y" : { "z": null } } """)
    }
  }

  "TransformJsonBody.applyBodyTransformations" should {
    "return empty buffer if dropping body without set ops" in {
      val bodyOps = ResolvedBodyOps(set = None, drop = Some(true))
      applyBodyTransformations(bodyOps, new JsonObject().put("x", "y")) mustBe Buffer.buffer()
    }

    "return empty buffer if dropping body with set ops" in {
      val bodyOps = ResolvedBodyOps(set = Some(Map(Path("x") -> Some(StringJsonValue("a")))), drop = Some(true))
      applyBodyTransformations(bodyOps, new JsonObject().put("x", "y")) mustBe Buffer.buffer()
    }
  }

}
