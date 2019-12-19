package com.cloudentity.pyron.plugin.impl.transformer

import com.cloudentity.pyron.domain.flow.PathParams
import com.cloudentity.pyron.plugin.util.value._
import com.cloudentity.pyron.test.TestRequestResponseCtx
import com.cloudentity.tools.vertx.http.Headers
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.{JsonArray, JsonObject}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class TransformRequestPluginTest extends WordSpec with MustMatchers with TestRequestResponseCtx {
  val body = new JsonObject()
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
      setJsonBody(Map(Path("a") -> Option(StringJsonValue("string"))))(emptyBody) must be (new JsonObject().put("a", "string"))
    }
    "set string value deep in empty body" in {
      setJsonBody(Map(Path("a", "b") -> Option(StringJsonValue("string"))))(emptyBody) must be (new JsonObject().put("a", new JsonObject().put("b", "string")))
    }
    "set json object value in empty body" in {
      val obj = new JsonObject().put("x", "y")
      setJsonBody(Map(Path("a") -> Option(ObjectJsonValue(obj.copy()))))(emptyBody) must be (new JsonObject().put("a", obj.copy()))
    }
    "set json array value in empty body" in {
      val arr = new JsonArray().add("x")
      setJsonBody(Map(Path("a") -> Option(ArrayJsonValue(arr.copy()))))(emptyBody) must be (new JsonObject().put("a", arr.copy()))
    }
    "set null value in empty body" in {
      setJsonBody(Map(Path("a") -> Option(NullJsonValue)))(emptyBody) must be (new JsonObject().put("a", null.asInstanceOf[String]))
    }

    def shallowBody = new JsonObject().put("x", "value")
    "set string value in shallow" in {
      setJsonBody(Map(Path("a") -> Option(StringJsonValue("string"))))(shallowBody) must be (new JsonObject().put("a", "string").put("x", "value"))
    }
    "set string value deep in shallow" in {
      setJsonBody(Map(Path("a", "b") -> Option(StringJsonValue("string"))))(shallowBody) must be (new JsonObject().put("a", new JsonObject().put("b", "string")).put("x", "value"))
    }
    "set json object value in shallow" in {
      val obj = new JsonObject().put("x", "y")
      setJsonBody(Map(Path("a") -> Option(ObjectJsonValue(obj.copy()))))(shallowBody) must be (new JsonObject().put("a", obj.copy()).put("x", "value"))
    }
    "set json array value in shallow" in {
      val arr = new JsonArray().add("x")
      setJsonBody(Map(Path("a") -> Option(ArrayJsonValue(arr.copy()))))(shallowBody) must be (new JsonObject().put("a", arr.copy()).put("x", "value"))
    }
    "set null value in shallow" in {
      setJsonBody(Map(Path("a") -> Option(NullJsonValue)))(shallowBody) must be (new JsonObject().put("a", null.asInstanceOf[String]).put("x", "value"))
    }

    "overwrite with string value in shallow" in {
      setJsonBody(Map(Path("x") -> Option(StringJsonValue("string"))))(shallowBody) must be (new JsonObject().put("x", "string"))
    }
    "overwrite with json object value in shallow" in {
      val obj = new JsonObject().put("x", "y")
      setJsonBody(Map(Path("x") -> Option(ObjectJsonValue(obj.copy()))))(shallowBody) must be (new JsonObject().put("x", obj.copy()))
    }
    "overwrite with json array value in shallow" in {
      val arr = new JsonArray().add("x")
      setJsonBody(Map(Path("x") -> Option(ArrayJsonValue(arr.copy()))))(shallowBody) must be (new JsonObject().put("x", arr.copy()))
    }
    "overwrite with null value in shallow" in {
      setJsonBody(Map(Path("x") -> Option(NullJsonValue)))(shallowBody) must be (new JsonObject().put("x", null.asInstanceOf[String]))
    }
    "overwrite with null in shallow if reference missing" in {
      setJsonBody(Map(Path("x") -> None))(shallowBody) must be (new JsonObject().put("x", null.asInstanceOf[String]))
    }

    def complexBody = new JsonObject().put("x", "value").put("y", new JsonObject().put("z", "value"))
    "overwrite with string value in complex body" in {
      setJsonBody(Map(Path("y", "z") -> Option(StringJsonValue("string"))))(complexBody) must be (new JsonObject().put("x", "value").put("y", new JsonObject().put("z", "string")))
    }
    "overwrite with json object value in complex body" in {
      val obj = new JsonObject().put("x", "y")
      setJsonBody(Map(Path("y", "z") -> Option(ObjectJsonValue(obj.copy()))))(complexBody) must be (new JsonObject().put("x", "value").put("y", new JsonObject().put("z", obj.copy())))
    }
    "overwrite with json array value in complex body" in {
      val arr = new JsonArray().add("x")
      setJsonBody(Map(Path("y", "z") -> Option(ArrayJsonValue(arr.copy()))))(complexBody) must be (new JsonObject().put("x", "value").put("y", new JsonObject().put("z", arr.copy())))
    }
    "overwrite with null value in complex body" in {
      setJsonBody(Map(Path("y", "z") -> Option(NullJsonValue)))(complexBody) must be (new JsonObject().put("x", "value").put("y", new JsonObject().put("z", null.asInstanceOf[String])))
    }
    "overwrite with null in complex deep if reference missing" in {
      setJsonBody(Map(Path("y", "z") -> None))(complexBody) must be (new JsonObject().put("x", "value").put("y", new JsonObject().put("z", null.asInstanceOf[String])))
    }
  }

  "TransformJsonBody.applyBodyTransformations" should {
    "return empty buffer if dropping body without set ops" in {
      TransformJsonBody.applyBodyTransformations(ResolvedBodyOps(None, Some(true)), new JsonObject()) must be(Buffer.buffer())
    }

    "return empty buffer if dropping body with set ops" in {
      TransformJsonBody.applyBodyTransformations(ResolvedBodyOps(Some(Map(Path("x") -> Some(StringJsonValue("a")))), Some(true)), new JsonObject()) must be(Buffer.buffer())
    }
  }

  "TransformPathParams.set" should {
    val setPathParams = TransformPathParams.setPathParams _

    "set value for non-existing param" in {
      setPathParams(Map("a" -> Option("string")))(PathParams(Map())) must be (PathParams(Map("a" -> "string")))
    }

    "set value for existing param" in {
      setPathParams(Map("a" -> Option("string")))(PathParams(Map("a" -> "x"))) must be (PathParams(Map("a" -> "string")))
    }

    "set none value for non-existing param" in {
      setPathParams(Map("a" -> None))(PathParams(Map())) must be (PathParams(Map()))
    }

    "set none value for existing param" in {
      setPathParams(Map("a" -> None))(PathParams(Map("a" -> "x"))) must be (PathParams(Map()))
    }
  }

  "TransformPathParams.set" should {
    val setHeaders = TransformHeaders.setHeaders _

    "set value for non-existing header" in {
      setHeaders(Map("a" -> Option(List("x"))))(Headers()) must be (Headers.of("a" -> "x"))
    }

    "set multi-value for non-existing header" in {
      setHeaders(Map("a" -> Option(List("x", "y"))))(Headers()) must be (Headers("a" -> List("x", "y")))
    }

    "set value for existing header" in {
      setHeaders(Map("a" -> Option(List("x"))))(Headers.of("a" -> "y")) must be (Headers.of("a" -> "x"))
    }

    "set none value for non-existing header" in {
      setHeaders(Map("a" -> None))(Headers()) must be (Headers())
    }

    "set none value for existing header" in {
      setHeaders(Map("a" -> None))(Headers.of("a" -> "x")) must be (Headers.of())
    }
  }
}
