package com.cloudentity.pyron.plugin.impl.transformer

import com.cloudentity.pyron.domain.flow.{AuthnCtx, PathParams}
import com.cloudentity.pyron.test.TestRequestResponseCtx
import com.cloudentity.tools.vertx.http.Headers
import io.circe.Json
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

  val ctxWithBody = emptyRequestCtx.modifyRequest(_.copy(bodyOpt = Some(body.toBuffer)))

  val authn =
    AuthnCtx(
      "shallow-string" -> Json.fromString(""),
      "shallow-object" -> Json.obj(),
      "shallow-array" -> Json.arr(),
      "shallow-boolean" -> Json.fromBoolean(false),
      "shallow-float" -> Json.fromDoubleOrNull(1.0f),
      "shallow-double" -> Json.fromDoubleOrNull(1.0d),
      "shallow-int" -> Json.fromInt(1),
      "shallow-null" -> Json.Null,
      "deep" -> Json.obj(
        "string" -> Json.fromString(""),
        "object" -> Json.obj(),
        "array" -> Json.arr(),
        "boolean" -> Json.fromBoolean(false),
        "float" -> Json.fromDoubleOrNull(1.0f),
        "double" -> Json.fromDoubleOrNull(1.0d),
        "int" -> Json.fromInt(1),
        "null" -> Json.Null
      )
    )

  val ctxWithAuthn = emptyRequestCtx.copy(authnCtx = authn)

  val pathParams = PathParams(Map("a" -> "value"))
  val ctxWithPathParams = emptyRequestCtx.modifyRequest(_.modifyPathParams(_ => pathParams))

  val headers = Headers("a" -> List("x", "y"))
  val ctxWithHeaders = emptyRequestCtx.modifyRequest(_.modifyHeaders(_ => headers))

  // resolve from body

  "ResolveValue.resolveString from body" should {
    val resolveString = ResolveValue.resolveString _

    "resolve shallow string" in {
      resolveString(ctxWithBody, Some(body), BodyRef(Path("shallow-string"))) must be(Some(""))
    }
    "resolve deep string" in {
      resolveString(ctxWithBody, Some(body), BodyRef(Path("deep", "string"))) must be(Some(""))
    }
    "resolve shallow boolean" in {
      resolveString(ctxWithBody, Some(body), BodyRef(Path("shallow-boolean"))) must be(Some("false"))
    }
    "resolve deep boolean" in {
      resolveString(ctxWithBody, Some(body), BodyRef(Path("deep", "boolean"))) must be(Some("false"))
    }
    "resolve shallow float" in {
      resolveString(ctxWithBody, Some(body), BodyRef(Path("shallow-float"))) must be(Some("1.0"))
    }
    "resolve deep float" in {
      resolveString(ctxWithBody, Some(body), BodyRef(Path("deep", "float"))) must be(Some("1.0"))
    }
    "resolve shallow int" in {
      resolveString(ctxWithBody, Some(body), BodyRef(Path("shallow-int"))) must be(Some("1"))
    }
    "resolve deep int" in {
      resolveString(ctxWithBody, Some(body), BodyRef(Path("deep", "int"))) must be(Some("1"))
    }
    "resolve shallow double" in {
      resolveString(ctxWithBody, Some(body), BodyRef(Path("shallow-float"))) must be(Some("1.0"))
    }
    "resolve deep double" in {
      resolveString(ctxWithBody, Some(body), BodyRef(Path("deep", "double"))) must be(Some("1.0"))
    }

    "fail to resolve shallow null value" in {
      resolveString(ctxWithBody, Some(body), BodyRef(Path("shallow-null"))) must be(None)
    }
    "fail to resolve shallow object value" in {
      resolveString(ctxWithBody, Some(body), BodyRef(Path("shallow-object"))) must be(None)
    }
    "fail to resolve shallow array value" in {
      resolveString(ctxWithBody, Some(body), BodyRef(Path("shallow-array"))) must be(None)
    }
    "fail to resolve deep null value" in {
      resolveString(ctxWithBody, Some(body), BodyRef(Path("deep", "null"))) must be(None)
    }
    "fail to resolve deep object value" in {
      resolveString(ctxWithBody, Some(body), BodyRef(Path("deep", "object"))) must be(None)
    }
    "fail to resolve deep array value" in {
      resolveString(ctxWithBody, Some(body), BodyRef(Path("deep", "array"))) must be(None)
    }

    "fail to resolve missing shallow value" in {
      resolveString(ctxWithBody, Some(body), BodyRef(Path("x"))) must be(None)
    }
    "fail to resolve missing deep value" in {
      resolveString(ctxWithBody, Some(body), BodyRef(Path("deep", "x"))) must be(None)
    }
  }

  "ResolveValue.resolveListOfStrings from body" should {
    val resolveListOfStrings = ResolveValue.resolveListOfStrings _

    "resolve shallow string" in {
      resolveListOfStrings(ctxWithBody, Some(body), BodyRef(Path("shallow-string"))) must be(Some(List("")))
    }
    "resolve deep string" in {
      resolveListOfStrings(ctxWithBody, Some(body), BodyRef(Path("deep", "string"))) must be(Some(List("")))
    }
    "resolve shallow boolean" in {
      resolveListOfStrings(ctxWithBody, Some(body), BodyRef(Path("shallow-boolean"))) must be(Some(List("false")))
    }
    "resolve deep boolean" in {
      resolveListOfStrings(ctxWithBody, Some(body), BodyRef(Path("deep", "boolean"))) must be(Some(List("false")))
    }
    "resolve shallow float" in {
      resolveListOfStrings(ctxWithBody, Some(body), BodyRef(Path("shallow-float"))) must be(Some(List("1.0")))
    }
    "resolve deep float" in {
      resolveListOfStrings(ctxWithBody, Some(body), BodyRef(Path("deep", "float"))) must be(Some(List("1.0")))
    }
    "resolve shallow int" in {
      resolveListOfStrings(ctxWithBody, Some(body), BodyRef(Path("shallow-int"))) must be(Some(List("1")))
    }
    "resolve deep int" in {
      resolveListOfStrings(ctxWithBody, Some(body), BodyRef(Path("deep", "int"))) must be(Some(List("1")))
    }
    "resolve shallow double" in {
      resolveListOfStrings(ctxWithBody, Some(body), BodyRef(Path("shallow-float"))) must be(Some(List("1.0")))
    }
    "resolve deep double" in {
      resolveListOfStrings(ctxWithBody, Some(body), BodyRef(Path("deep", "double"))) must be(Some(List("1.0")))
    }
    "resolve shallow array" in {
      resolveListOfStrings(ctxWithBody, Some(body), BodyRef(Path("shallow-array"))) must be(Some(List()))
    }
    "resolve deep array" in {
      resolveListOfStrings(ctxWithBody, Some(body), BodyRef(Path("deep", "array"))) must be(Some(List()))
    }

    "fail to resolve shallow null value" in {
      resolveListOfStrings(ctxWithBody, Some(body), BodyRef(Path("shallow-null"))) must be(None)
    }
    "fail to resolve shallow object value" in {
      resolveListOfStrings(ctxWithBody, Some(body), BodyRef(Path("shallow-object"))) must be(None)
    }
    "fail to resolve deep null value" in {
      resolveListOfStrings(ctxWithBody, Some(body), BodyRef(Path("deep", "null"))) must be(None)
    }
    "fail to resolve deep object value" in {
      resolveListOfStrings(ctxWithBody, Some(body), BodyRef(Path("deep", "object"))) must be(None)
    }

    "fail to resolve missing shallow value" in {
      resolveListOfStrings(ctxWithBody, Some(body), BodyRef(Path("x"))) must be(None)
    }
    "fail to resolve missing deep value" in {
      resolveListOfStrings(ctxWithBody, Some(body), BodyRef(Path("deep", "x"))) must be(None)
    }
  }

  "ResolveValue.resolveJson from body" should {
    val resolveJson = ResolveValue.resolveJson _

    "resolve shallow string" in {
      resolveJson(ctxWithBody, Some(body), BodyRef(Path("shallow-string"))) must be(Some(StringJsonValue("")))
    }
    "resolve deep string" in {
      resolveJson(ctxWithBody, Some(body), BodyRef(Path("deep", "string"))) must be(Some(StringJsonValue("")))
    }

    "resolve shallow object" in {
      resolveJson(ctxWithBody, Some(body), BodyRef(Path("shallow-object"))) must be(Some(ObjectJsonValue(new JsonObject())))
    }
    "resolve deep object" in {
      resolveJson(ctxWithBody, Some(body), BodyRef(Path("deep", "object"))) must be(Some(ObjectJsonValue(new JsonObject())))
    }

    "resolve shallow array" in {
      resolveJson(ctxWithBody, Some(body), BodyRef(Path("shallow-array"))) must be(Some(ArrayJsonValue(new JsonArray())))
    }
    "resolve deep array" in {
      resolveJson(ctxWithBody, Some(body), BodyRef(Path("deep", "array"))) must be(Some(ArrayJsonValue(new JsonArray())))
    }

    "resolve shallow boolean" in {
      resolveJson(ctxWithBody, Some(body), BodyRef(Path("shallow-boolean"))) must be(Some(BooleanJsonValue(false)))
    }
    "resolve deep boolean" in {
      resolveJson(ctxWithBody, Some(body), BodyRef(Path("deep", "boolean"))) must be(Some(BooleanJsonValue(false)))
    }

    "resolve shallow float" in {
      resolveJson(ctxWithBody, Some(body), BodyRef(Path("shallow-float"))) must be(Some(NumberJsonValue(1.0f)))
    }
    "resolve deep float" in {
      resolveJson(ctxWithBody, Some(body), BodyRef(Path("deep", "float"))) must be(Some(NumberJsonValue(1.0f)))
    }

    "resolve shallow double" in {
      resolveJson(ctxWithBody, Some(body), BodyRef(Path("shallow-double"))) must be(Some(NumberJsonValue(1.0d)))
    }
    "resolve deep double" in {
      resolveJson(ctxWithBody, Some(body), BodyRef(Path("deep", "double"))) must be(Some(NumberJsonValue(1.0d)))
    }

    "resolve shallow int" in {
      resolveJson(ctxWithBody, Some(body), BodyRef(Path("shallow-int"))) must be(Some(NumberJsonValue(1)))
    }
    "resolve deep int" in {
      resolveJson(ctxWithBody, Some(body), BodyRef(Path("deep", "int"))) must be(Some(NumberJsonValue(1)))
    }

    "resolve shallow null" in {
      resolveJson(ctxWithBody, Some(body), BodyRef(Path("shallow-null"))) must be(Some(NullJsonValue))
    }
    "resolve deep null" in {
      resolveJson(ctxWithBody, Some(body), BodyRef(Path("deep", "null"))) must be(Some(NullJsonValue))
    }
  }

  // resolve from authn

  "ResolveValue.resolveString from authn" should {
    val resolveString = ResolveValue.resolveString _

    "resolve shallow string" in {
      resolveString(ctxWithAuthn, None, AuthnRef(Path("shallow-string"))) must be(Some(""))
    }
    "resolve deep string" in {
      resolveString(ctxWithAuthn, None, AuthnRef(Path("deep", "string"))) must be(Some(""))
    }
    "resolve shallow boolean" in {
      resolveString(ctxWithAuthn, None, AuthnRef(Path("shallow-boolean"))) must be(Some("false"))
    }
    "resolve deep boolean" in {
      resolveString(ctxWithAuthn, None, AuthnRef(Path("deep", "boolean"))) must be(Some("false"))
    }
    "resolve shallow float" in {
      resolveString(ctxWithAuthn, None, AuthnRef(Path("shallow-float"))) must be(Some("1"))
    }
    "resolve deep float" in {
      resolveString(ctxWithAuthn, None, AuthnRef(Path("deep", "float"))) must be(Some("1"))
    }
    "resolve shallow int" in {
      resolveString(ctxWithAuthn, None, AuthnRef(Path("shallow-int"))) must be(Some("1"))
    }
    "resolve deep int" in {
      resolveString(ctxWithAuthn, None, AuthnRef(Path("deep", "int"))) must be(Some("1"))
    }
    "resolve shallow double" in {
      resolveString(ctxWithAuthn, None, AuthnRef(Path("shallow-float"))) must be(Some("1"))
    }
    "resolve deep double" in {
      resolveString(ctxWithAuthn, None, AuthnRef(Path("deep", "double"))) must be(Some("1"))
    }

    "fail to resolve shallow null value" in {
      resolveString(ctxWithAuthn, None, AuthnRef(Path("shallow-null"))) must be(None)
    }
    "fail to resolve shallow object value" in {
      resolveString(ctxWithAuthn, None, AuthnRef(Path("shallow-object"))) must be(None)
    }
    "fail to resolve shallow array value" in {
      resolveString(ctxWithAuthn, None, AuthnRef(Path("shallow-array"))) must be(None)
    }
    "fail to resolve deep null value" in {
      resolveString(ctxWithAuthn, None, AuthnRef(Path("deep", "null"))) must be(None)
    }
    "fail to resolve deep object value" in {
      resolveString(ctxWithAuthn, None, AuthnRef(Path("deep", "object"))) must be(None)
    }
    "fail to resolve deep array value" in {
      resolveString(ctxWithAuthn, None, AuthnRef(Path("deep", "array"))) must be(None)
    }

    "fail to resolve missing shallow value" in {
      resolveString(ctxWithAuthn, None, AuthnRef(Path("x"))) must be(None)
    }
    "fail to resolve missing deep value" in {
      resolveString(ctxWithAuthn, None, AuthnRef(Path("deep", "x"))) must be(None)
    }
  }

  "ResolveValue.resolveListOfStrings from authn" should {
    val resolveListOfStrings = ResolveValue.resolveListOfStrings _

    "resolve shallow string" in {
      resolveListOfStrings(ctxWithAuthn, None, AuthnRef(Path("shallow-string"))) must be(Some(List("")))
    }
    "resolve deep string" in {
      resolveListOfStrings(ctxWithAuthn, None, AuthnRef(Path("deep", "string"))) must be(Some(List("")))
    }
    "resolve shallow boolean" in {
      resolveListOfStrings(ctxWithAuthn, None, AuthnRef(Path("shallow-boolean"))) must be(Some(List("false")))
    }
    "resolve deep boolean" in {
      resolveListOfStrings(ctxWithAuthn, None, AuthnRef(Path("deep", "boolean"))) must be(Some(List("false")))
    }
    "resolve shallow float" in {
      resolveListOfStrings(ctxWithAuthn, None, AuthnRef(Path("shallow-float"))) must be(Some(List("1")))
    }
    "resolve deep float" in {
      resolveListOfStrings(ctxWithAuthn, None, AuthnRef(Path("deep", "float"))) must be(Some(List("1")))
    }
    "resolve shallow int" in {
      resolveListOfStrings(ctxWithAuthn, None, AuthnRef(Path("shallow-int"))) must be(Some(List("1")))
    }
    "resolve deep int" in {
      resolveListOfStrings(ctxWithAuthn, None, AuthnRef(Path("deep", "int"))) must be(Some(List("1")))
    }
    "resolve shallow double" in {
      resolveListOfStrings(ctxWithAuthn, None, AuthnRef(Path("shallow-float"))) must be(Some(List("1")))
    }
    "resolve deep double" in {
      resolveListOfStrings(ctxWithAuthn, None, AuthnRef(Path("deep", "double"))) must be(Some(List("1")))
    }
    "resolve shallow array" in {
      resolveListOfStrings(ctxWithAuthn, Some(body), AuthnRef(Path("shallow-array"))) must be(Some(List()))
    }
    "resolve deep array" in {
      resolveListOfStrings(ctxWithAuthn, Some(body), AuthnRef(Path("deep", "array"))) must be(Some(List()))
    }

    "fail to resolve shallow null value" in {
      resolveListOfStrings(ctxWithAuthn, None, AuthnRef(Path("shallow-null"))) must be(None)
    }
    "fail to resolve shallow object value" in {
      resolveListOfStrings(ctxWithAuthn, None, AuthnRef(Path("shallow-object"))) must be(None)
    }
    "fail to resolve deep null value" in {
      resolveListOfStrings(ctxWithAuthn, None, AuthnRef(Path("deep", "null"))) must be(None)
    }
    "fail to resolve deep object value" in {
      resolveListOfStrings(ctxWithAuthn, None, AuthnRef(Path("deep", "object"))) must be(None)
    }

    "fail to resolve missing shallow value" in {
      resolveListOfStrings(ctxWithAuthn, None, AuthnRef(Path("x"))) must be(None)
    }
    "fail to resolve missing deep value" in {
      resolveListOfStrings(ctxWithAuthn, None, AuthnRef(Path("deep", "x"))) must be(None)
    }
  }

  "ResolveValue.resolveJson from authn" should {
    val resolveJson = ResolveValue.resolveJson _

    "resolve shallow string" in {
      resolveJson(ctxWithAuthn, None, AuthnRef(Path("shallow-string"))) must be(Some(StringJsonValue("")))
    }
    "resolve deep string" in {
      resolveJson(ctxWithAuthn, None, AuthnRef(Path("deep", "string"))) must be(Some(StringJsonValue("")))
    }

    "resolve shallow object" in {
      resolveJson(ctxWithAuthn, None, AuthnRef(Path("shallow-object"))) must be(Some(ObjectJsonValue(new JsonObject())))
    }
    "resolve deep object" in {
      resolveJson(ctxWithAuthn, None, AuthnRef(Path("deep", "object"))) must be(Some(ObjectJsonValue(new JsonObject())))
    }

    "resolve shallow array" in {
      resolveJson(ctxWithAuthn, None, AuthnRef(Path("shallow-array"))) must be(Some(ArrayJsonValue(new JsonArray())))
    }
    "resolve deep array" in {
      resolveJson(ctxWithAuthn, None, AuthnRef(Path("deep", "array"))) must be(Some(ArrayJsonValue(new JsonArray())))
    }

    "resolve shallow boolean" in {
      resolveJson(ctxWithAuthn, None, AuthnRef(Path("shallow-boolean"))) must be(Some(BooleanJsonValue(false)))
    }
    "resolve deep boolean" in {
      resolveJson(ctxWithAuthn, None, AuthnRef(Path("deep", "boolean"))) must be(Some(BooleanJsonValue(false)))
    }

    "resolve shallow float" in {
      resolveJson(ctxWithAuthn, None, AuthnRef(Path("shallow-float"))) must be(Some(NumberJsonValue(1.0f)))
    }
    "resolve deep float" in {
      resolveJson(ctxWithAuthn, None, AuthnRef(Path("deep", "float"))) must be(Some(NumberJsonValue(1.0f)))
    }

    "resolve shallow double" in {
      resolveJson(ctxWithAuthn, None, AuthnRef(Path("shallow-double"))) must be(Some(NumberJsonValue(1.0d)))
    }
    "resolve deep double" in {
      resolveJson(ctxWithAuthn, None, AuthnRef(Path("deep", "double"))) must be(Some(NumberJsonValue(1.0d)))
    }

    "resolve shallow int" in {
      resolveJson(ctxWithAuthn, None, AuthnRef(Path("shallow-int"))) must be(Some(NumberJsonValue(1)))
    }
    "resolve deep int" in {
      resolveJson(ctxWithAuthn, None, AuthnRef(Path("deep", "int"))) must be(Some(NumberJsonValue(1)))
    }

    "resolve shallow null" in {
      resolveJson(ctxWithAuthn, None, AuthnRef(Path("shallow-null"))) must be(Some(NullJsonValue))
    }
    "resolve deep null" in {
      resolveJson(ctxWithAuthn, None, AuthnRef(Path("deep", "null"))) must be(Some(NullJsonValue))
    }
  }

  // resolve from pathParams

  "ResolveValue.resolveString from pathParams" should {
    val resolveString = ResolveValue.resolveString _

    "resolve existing param" in {
      resolveString(ctxWithPathParams, None, PathParamRef("a")) must be(Some("value"))
    }

    "fail to resolve missing param" in {
      resolveString(ctxWithPathParams, None, PathParamRef("x")) must be(None)
    }
  }

  "ResolveValue.resolveListOfStrings from pathParams" should {
    val resolveListOfStrings = ResolveValue.resolveListOfStrings _

    "resolve existing param" in {
      resolveListOfStrings(ctxWithPathParams, None, PathParamRef("a")) must be(Some(List("value")))
    }

    "fail to resolve missing param" in {
      resolveListOfStrings(ctxWithPathParams, None, PathParamRef("x")) must be(None)
    }
  }

  "ResolveValue.resolveJson from pathParams" should {
    val resolveJson = ResolveValue.resolveJson _

    "resolve existing param" in {
      resolveJson(ctxWithPathParams, None, PathParamRef("a")) must be(Some(StringJsonValue("value")))
    }

    "fail to resolve missing param" in {
      resolveJson(ctxWithPathParams, None, PathParamRef("x")) must be(None)
    }

    // resolve from headers

    "ResolveValue.resolveString from headers" should {
      val resolveString = ResolveValue.resolveString _

      "resolve first value of existing header if first-value ref type" in {
        resolveString(ctxWithHeaders, None, HeaderRef("a", FirstHeaderRefType)) must be(Some("x"))
      }

      "resolve first value of existing header if all-values ref type" in { // if we reach this case, then someone mis-configured the plugin - trying to set a list of string to attribute that expects a string (e.g. path-param)
        resolveString(ctxWithHeaders, None, HeaderRef("a", AllHeaderRefType)) must be(Some("x"))
      }

      "fail to resolve missing header" in {
        resolveString(ctxWithHeaders, None, HeaderRef("x", FirstHeaderRefType)) must be(None)
      }
    }

    "ResolveValue.resolveListOfStrings from headers" should {
      val resolveListOfStrings = ResolveValue.resolveListOfStrings _

      "resolve first value of existing header if first-value ref type" in {
        resolveListOfStrings(ctxWithHeaders, None, HeaderRef("a", FirstHeaderRefType)) must be(Some(List("x")))
      }

      "resolve first value of existing header if all-values ref type" in {
        resolveListOfStrings(ctxWithHeaders, None, HeaderRef("a", AllHeaderRefType)) must be(Some(List("x", "y")))
      }

      "fail to resolve missing header" in {
        resolveListOfStrings(ctxWithHeaders, None, HeaderRef("x", FirstHeaderRefType)) must be(None)
      }
    }

    "ResolveValue.resolveJson from headers" should {
      val resolveJson = ResolveValue.resolveJson _

      "resolve first value of existing header if first-value ref type" in {
        resolveJson(ctxWithHeaders, None, HeaderRef("a", FirstHeaderRefType)) must be(Some(StringJsonValue("x")))
      }

      "resolve first value of existing header if all-values ref type" in {
        resolveJson(ctxWithHeaders, None, HeaderRef("a", AllHeaderRefType)) must be(Some(ArrayJsonValue(new JsonArray().add("x").add("y"))))
      }

      "fail to resolve missing header" in {
        resolveJson(ctxWithHeaders, None, HeaderRef("x", FirstHeaderRefType)) must be(None)
      }
    }
  }

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
