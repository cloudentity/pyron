package com.cloudentity.pyron.plugin.impl.transformer

import com.cloudentity.pyron.domain.flow.PathParams
import com.cloudentity.pyron.domain.http.QueryParams
import com.cloudentity.pyron.plugin.util.value._
import com.cloudentity.pyron.test.TestRequestResponseCtx
import com.cloudentity.tools.vertx.http.Headers
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.{JsonArray, JsonObject}
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class TransformRequestPluginTest extends WordSpec with MustMatchers with TestRequestResponseCtx {
  val body: JsonObject = new JsonObject()
    .put("shallow-string", "")
    .put("shallow-object", new JsonObject())
    .put("shallow-array", new JsonArray())
    .put("shallow-boolean", false)
    .put("shallow-float", 1.0f)
    .put("shallow-double", 1.0d)
    .put("shallow-int", 1)
    .put("shallow-null", null.asInstanceOf[String])
    .put("deep",
      new JsonObject()
        .put("string", "")
        .put("object", new JsonObject())
        .put("array", new JsonArray())
        .put("boolean", false)
        .put("float", 1.0f)
        .put("double", 1.0d)
        .put("int", 1)
        .put("null", null.asInstanceOf[String])
    )

  "TransformJsonBody.setJsonBody" should {
    val setJsonBody = TransformJsonBody.setJsonBody _

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
      TransformJsonBody.applyBodyTransformations(bodyOps, new JsonObject().put("x", "y")) mustBe Buffer.buffer()
    }

    "return empty buffer if dropping body with set ops" in {
      val bodyOps = ResolvedBodyOps(set = Some(Map(Path("x") -> Some(StringJsonValue("a")))), drop = Some(true))
      TransformJsonBody.applyBodyTransformations(bodyOps, new JsonObject().put("x", "y")) mustBe Buffer.buffer()
    }
  }

  "TransformPathParams.set" should {
    val setPathParams = TransformPathParams.setPathParams _

    "set value for non-existing param" in {
      setPathParams(Map("a" -> Option("string")))(PathParams(Map())) mustBe PathParams(Map("a" -> "string"))
    }

    "set value for existing param" in {
      setPathParams(Map("a" -> Option("string")))(PathParams(Map("a" -> "x"))) mustBe PathParams(Map("a" -> "string"))
    }

    "set none value for non-existing param" in {
      setPathParams(Map("a" -> None))(PathParams(Map())) mustBe PathParams(Map())
    }

    "set none value for existing param" in {
      setPathParams(Map("a" -> None))(PathParams(Map("a" -> "x"))) mustBe PathParams(Map())
    }
  }

  "TransformQueryParams.set" should {
    val setQueryParams = TransformQueryParams.setQueryParams _

    "set value for non-existing query param" in {
      setQueryParams(Map("a" -> Option(List("x"))))(QueryParams.of()) mustBe QueryParams.of("a" -> "x")
    }

    "set multi-value for non-existing query param" in {
      setQueryParams(Map("a" -> Option(List("x", "y"))))(QueryParams.of()) mustBe QueryParams.of("a" -> "x", "a" -> "y")
    }

    "set value for existing query param" in {
      setQueryParams(Map("a" -> Option(List("x"))))(QueryParams.of("a" -> "y")) mustBe QueryParams.of("a" -> "x")
    }

    "set none value for non-existing query param" in {
      setQueryParams(Map("a" -> None))(QueryParams.of()) mustBe QueryParams.of()
    }

    "set none value for existing header" in {
      setQueryParams(Map("a" -> None))(QueryParams.of("a" -> "x")) mustBe QueryParams()
    }
  }

  "TransformHeaders.set" should {
    val setHeaders = TransformHeaders.setHeaders _

    "set value for non-existing header" in {
      setHeaders(Map("a" -> Option(List("x"))))(Headers()) mustBe Headers.of("a" -> "x")
    }

    "set multi-value for non-existing header" in {
      setHeaders(Map("a" -> Option(List("x", "y"))))(Headers()) mustBe Headers.of("a" -> "x", "a" -> "y")
    }

    "set value for existing header" in {
      setHeaders(Map("a" -> Option(List("x"))))(Headers.of("a" -> "y")) mustBe Headers.of("a" -> "x")
    }

    "set none value for non-existing header" in {
      setHeaders(Map("a" -> None))(Headers()) mustBe Headers()
    }

    "set none value for existing header" in {
      setHeaders(Map("a" -> None))(Headers.of("a" -> "x")) mustBe Headers()
    }
  }
}
