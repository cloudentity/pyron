package com.cloudentity.pyron.plugin.impl.transform

import com.cloudentity.pyron.plugin.impl.transform.TransformJsonBody.{applyBodyTransformations, setJsonBody}
import com.cloudentity.pyron.plugin.util.value._
import com.cloudentity.pyron.plugin.util.value.{JsonValueIgnoreNullIfDefault => JsonWrap}
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
      val valAtPath = Map(Path("a") -> JsonWrap(Option(StringJsonValue("string"))))
      setJsonBody(valAtPath, Nil)(emptyBody) mustBe emptyBody.put("a", "string")
    }
    "set string value deep in empty body" in {
      val valAtPath = Map(Path("a", "b") -> JsonWrap(Option(StringJsonValue("string"))))
      setJsonBody(valAtPath, Nil)(emptyBody) mustBe emptyBody.put("a", emptyBody.put("b", "string"))
    }
    "set json object value in empty body" in {
      val obj = new JsonObject().put("x", "y")
      val valAtPath = Map(Path("a") -> JsonWrap(Option(ObjectJsonValue(obj.copy()))))
      setJsonBody(valAtPath, Nil)(emptyBody) mustBe emptyBody.put("a", obj.copy())
    }
    "set json array value in empty body" in {
      val arr = new JsonArray().add("x")
      val valAtPath = Map(Path("a") -> JsonWrap(Option(ArrayJsonValue(arr.copy()))))
      setJsonBody(valAtPath, Nil)(emptyBody) mustBe emptyBody.put("a", arr.copy())
    }
    "set null value in empty body" in {
      val valAtPath = Map(Path("a") -> JsonWrap(Option(NullJsonValue)))
      setJsonBody(valAtPath, Nil)(emptyBody) mustBe emptyBody.put("a", null.asInstanceOf[String])
    }

    def shallowBody = new JsonObject().put("x", "value")

    "set string value in shallow" in {
      val valAtPath = Map(Path("a") -> JsonWrap(Option(StringJsonValue("string"))))
      setJsonBody(valAtPath, Nil)(shallowBody) mustBe shallowBody.put("a", "string")
    }
    "set string value deep in shallow" in {
      val valAtPath = Map(Path("a", "b") -> JsonWrap(Option(StringJsonValue("string"))))
      setJsonBody(valAtPath, Nil)(shallowBody) mustBe shallowBody.put("a", new JsonObject().put("b", "string"))
    }
    "set json object value in shallow" in {
      val obj = new JsonObject().put("x", "y")
      val valAtPath = Map(Path("a") -> JsonWrap(Option(ObjectJsonValue(obj.copy()))))
      setJsonBody(valAtPath, Nil)(shallowBody) mustBe shallowBody.put("a", obj.copy())
    }
    "set json array value in shallow" in {
      val arr = new JsonArray().add("x")
      val valAtPath = Map(Path("a") -> JsonWrap(Option(ArrayJsonValue(arr.copy()))))
      setJsonBody(valAtPath, Nil)(shallowBody) mustBe shallowBody.put("a", arr.copy())
    }
    "set null value in shallow" in {
      val valAtPath = Map(Path("a") -> JsonWrap(Option(NullJsonValue)))
      setJsonBody(valAtPath, Nil)(shallowBody) mustBe shallowBody.put("a", null.asInstanceOf[String])
    }

    "overwrite with string value in shallow" in {
      val valAtPath = Map(Path("x") -> JsonWrap(Option(StringJsonValue("string"))))
      setJsonBody(valAtPath, Nil)(shallowBody) mustBe emptyBody.put("x", "string")
    }
    "overwrite with json object value in shallow" in {
      val obj = new JsonObject().put("u", "v")
      val valAtPath = Map(Path("x") -> JsonWrap(Option(ObjectJsonValue(obj.copy()))))
      setJsonBody(valAtPath, Nil)(shallowBody) mustBe emptyBody.put("x", obj.copy())
    }
    "overwrite with json array value in shallow" in {
      val arr = new JsonArray().add("x")
      val valAtPath = Map(Path("x") -> JsonWrap(Option(ArrayJsonValue(arr.copy()))))
      setJsonBody(valAtPath, Nil)(shallowBody) mustBe emptyBody.put("x", arr.copy())
    }
    "overwrite with null value in shallow" in {
      val valAtPath = Map(Path("x") -> JsonWrap(Option(NullJsonValue)))
      setJsonBody(valAtPath, Nil)(shallowBody) mustBe emptyBody.put("x", null.asInstanceOf[String])
    }
    "overwrite with null in shallow if reference missing" in {
      val valAtPath = Map(Path("x") -> JsonWrap(None))
      setJsonBody(valAtPath, Nil)(shallowBody) mustBe emptyBody.put("x", null.asInstanceOf[String])
    }
    "add null in shallow if reference missing" in {
      val valAtPath = Map(Path("z") -> JsonWrap(None))
      setJsonBody(valAtPath, Nil)(shallowBody) mustBe shallowBody.put("z", null.asInstanceOf[String])
    }
    "retain null value in shallow even if nullIfAbsent is false" in {
      val valAtPath = Map(Path("x") -> JsonWrap(Option(NullJsonValue)))
      setJsonBody(valAtPath, Nil, nullIfAbsent = false)(shallowBody) mustBe emptyBody.put("x", null.asInstanceOf[String])
    }
    "remove existing value in shallow if reference missing if nullIfAbsent is false" in {
      val valAtPath = Map(Path("x") -> JsonWrap(None))
      setJsonBody(valAtPath, Nil, nullIfAbsent = false)(shallowBody) mustBe emptyBody
    }
    "omit key in shallow if reference missing if nullIfAbsent is false" in {
      val valAtPath = Map(Path("z") -> JsonWrap(None))
      setJsonBody(valAtPath, Nil, nullIfAbsent = false)(shallowBody) mustBe shallowBody
    }
    "retain null value in shallow even if ignoreNullIfAbsent is true" in {
      val valAtPath = Map(Path("x") -> JsonWrap(Option(NullJsonValue), ignoreNullIfAbsent = true))
      setJsonBody(valAtPath, Nil)(shallowBody) mustBe emptyBody.put("x", null.asInstanceOf[String])
    }
    "remove existing value in shallow if reference missing if ignoreNullIfAbsent is true" in {
      val valAtPath = Map(Path("x") -> JsonWrap(None, ignoreNullIfAbsent = true))
      setJsonBody(valAtPath, Nil)(shallowBody) mustBe emptyBody
    }
    "omit key in shallow if reference missing if ignoreNullIfAbsent is true" in {
      val valAtPath = Map(Path("z") -> JsonWrap(None, ignoreNullIfAbsent = true))
      setJsonBody(valAtPath, Nil)(shallowBody) mustBe shallowBody
    }

    "remove existing value" in {
      val removePath = List(Path("x"))
      setJsonBody(Map.empty, removePath)(shallowBody) mustBe emptyBody
    }

    "remove existing value while adding new" in {
      val valAtPath = Map(Path("y") -> JsonWrap(Option(StringJsonValue("string"))))
      val removePath = List(Path("x"))
      setJsonBody(valAtPath, removePath)(shallowBody) mustBe emptyBody.put("y", "string")
    }

    "remove existing value while replacing" in {
      val valAtPath = Map(Path("x") -> JsonWrap(Option(StringJsonValue("string"))))
      val removePath = List(Path("x"))
      setJsonBody(valAtPath, removePath)(shallowBody) mustBe emptyBody
    }

    def complexBody = new JsonObject(""" { "x": "value", "y" : { "z": "value" } } """)

    "overwrite with string value in complex body" in {
      val valAtPath = Map(Path("y", "z") -> JsonWrap(Option(StringJsonValue("string"))))
      setJsonBody(valAtPath, Nil)(complexBody) mustBe
        new JsonObject(""" { "x": "value", "y" : { "z": "string" } } """)
    }
    "overwrite with json object value in complex body" in {
      val obj = new JsonObject().put("u", "v")
      val valAtPath = Map(Path("y", "z") -> JsonWrap(Option(ObjectJsonValue(obj))))
      setJsonBody(valAtPath, Nil)(complexBody) mustBe
        new JsonObject(""" { "x": "value", "y" : { "z": { "u" : "v" } } } """)
    }
    "overwrite with json array value in complex body" in {
      val arr = new JsonArray().add("u")
      val valAtPath = Map(Path("y", "z") -> JsonWrap(Option(ArrayJsonValue(arr))))
      setJsonBody(valAtPath, Nil)(complexBody) mustBe
        new JsonObject(""" { "x": "value", "y" : { "z": [ "u" ] } } """)
    }
    "overwrite with null value in complex body" in {
      val valAtPath = Map(Path("y", "z") -> JsonWrap(Option(NullJsonValue)))
      setJsonBody(valAtPath, Nil)(complexBody) mustBe
        new JsonObject(""" { "x": "value", "y" : { "z": null } } """)
    }
    "overwrite with null in complex deep if reference missing" in {
      val valAtPath = Map(Path("y", "z") -> JsonWrap(None))
      setJsonBody(valAtPath, Nil)(complexBody) mustBe
        new JsonObject(""" { "x": "value", "y" : { "z": null } } """)
    }

    "remove value in complex body" in {
      val removePath = List(Path("y", "z"))
      setJsonBody(Map.empty, removePath)(complexBody) mustBe
        new JsonObject(""" { "x": "value", "y" : {} } """)
    }

    "remove nested object in complex body" in {
      val removePath = List(Path("y"))
      setJsonBody(Map.empty, removePath)(complexBody) mustBe
        new JsonObject(""" { "x": "value" } """)
    }

    "remove multiple elements in complex body" in {
      val removePath = List(Path("x"), Path("y", "z"))
      setJsonBody(Map.empty, removePath)(complexBody) mustBe
        new JsonObject(""" { "y": {} } """)
    }

    def shallowBodyWithArray = new JsonObject(""" { "x": ["value0", "value1", "value2"] } """)

    "remove array from shallow body" in {
      val removePath = List(Path("x"))
      setJsonBody(Map.empty, removePath)(shallowBodyWithArray) mustBe emptyBody
    }

    "remove array element from shallow body" in {
      val removePath = List(Path("x", "[1]"))
      setJsonBody(Map.empty, removePath)(shallowBodyWithArray) mustBe emptyBody.put("x", new JsonArray("""["value0", "value2"]"""))
    }

    "do nothing if removing negative array element" in {
      val removePath = List(Path("x", "[-1]"))
      setJsonBody(Map.empty, removePath)(shallowBodyWithArray) mustBe shallowBodyWithArray
    }

    "do nothing if removing out-of-bounds array element" in {
      val removePath = List(Path("x", "[4]"))
      setJsonBody(Map.empty, removePath)(shallowBodyWithArray) mustBe shallowBodyWithArray
    }

    "do nothing if removing nonexistent nested array element" in {
      val removePath = List(Path("x", "[1]", "foo"))
      setJsonBody(Map.empty, removePath)(shallowBodyWithArray) mustBe shallowBodyWithArray
    }

    "do nothing if dereferencing an array without index notation" in {
      val removePath = List(Path("x", "y"))
      setJsonBody(Map.empty, removePath)(shallowBodyWithArray) mustBe shallowBodyWithArray
    }

    def complexBodyWithArray = new JsonObject(""" {"x":["value0","value1",{"x2":"value2","x3":["value3","value4"]}],"y":{"y1":["value1"]}} """)

    "remove array from complex body" in {
      val removePath = List(Path("x"), Path("y"))
      setJsonBody(Map.empty, removePath)(complexBodyWithArray) mustBe emptyBody
    }

    "remove deeply nested array elements" in {
      val removePath = List(Path("x", "[2]", "x3", "[0]"), Path("y", "y1", "[0]"))
      setJsonBody(Map.empty, removePath)(complexBodyWithArray) mustBe
        new JsonObject(""" {"x":["value0","value1",{"x2":"value2","x3":["value4"]}],"y":{"y1":[]}} """)
    }
  }

  "TransformJsonBody.applyBodyTransformations" should {
    "return empty buffer if dropping body without set ops" in {
      val bodyOps = ResolvedBodyOps(set = Map.empty, remove = None, drop = Some(true), nullIfAbsent = None)
      applyBodyTransformations(bodyOps, new JsonObject().put("x", "y")) mustBe Buffer.buffer()
    }

    "return empty buffer if dropping body with set ops" in {
      val bodyOps = ResolvedBodyOps(set = Map(Path("x") -> JsonWrap(Some(StringJsonValue("a")))), remove = None, drop = Some(true), nullIfAbsent = None)
      applyBodyTransformations(bodyOps, new JsonObject().put("x", "y")) mustBe Buffer.buffer()
    }
  }

}
