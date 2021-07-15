package com.cloudentity.pyron.plugin.util.value

import com.cloudentity.pyron.domain.flow.{AuthnCtx, PathParams, RequestCtx}
import com.cloudentity.pyron.plugin.util.value.ValueResolver.resolveString
import com.cloudentity.pyron.test.TestRequestResponseCtx
import com.cloudentity.tools.vertx.http.Headers
import io.circe.Json
import io.vertx.core.json.{JsonArray, JsonObject}
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class ValueResolverTest extends WordSpec with MustMatchers with TestRequestResponseCtx {
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
    .put("deep-array",
      new JsonArray()
        .add(new JsonObject()
          .put("string-0", "")
          .put("object-0", new JsonObject())
          .put("array-0", new JsonArray())
          .put("boolean-0", false)
          .put("float-0", 1.0f)
          .put("double-0", 1.0d)
          .put("int-0", 1)
          .put("null-0", null.asInstanceOf[String])
        )
        .add(new JsonObject()
          .put("string-1", "")
          .put("object-1", new JsonObject())
          .put("array-1", new JsonArray())
          .put("boolean-1", false)
          .put("float-1", 1.0f)
          .put("double-1", 1.0d)
          .put("int-1", 1)
          .put("null-1", null.asInstanceOf[String])
        )
        .add(new JsonArray()
          .add("deep-string-array-elt")
        )
        .add("string-array-elt")
        .add(false)
        .add(1.0f)
        .add(1.0d)
        .add(1)
        .add(null.asInstanceOf[String])
      )

  val conf: JsonObject = new JsonObject()

  val ctxWithBody: RequestCtx = emptyRequestCtx.modifyRequest(_.copy(bodyOpt = Some(body.toBuffer)))

  val authn: AuthnCtx =
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

  val ctxWithAuthn: RequestCtx = emptyRequestCtx.copy(authnCtx = authn)

  val pathParams: PathParams = PathParams(Map("a" -> "value"))
  val ctxWithPathParams: RequestCtx = emptyRequestCtx.modifyRequest(_.modifyPathParams(_ => pathParams))

  val headers: Headers = Headers("a" -> List("x", "y"))
  val ctxWithHeaders: RequestCtx = emptyRequestCtx.modifyRequest(_.modifyHeaders(_ => headers))

  // resolve from body

  "ValueResolver.resolveString from body" should {

    "resolve shallow string" in {
      resolveString(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-string"))) mustBe Some("")
    }
    "resolve deep string" in {
      resolveString(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "string"))) mustBe Some("")
    }
    "resolve shallow boolean" in {
      resolveString(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-boolean"))) mustBe Some("false")
    }
    "resolve deep boolean" in {
      resolveString(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "boolean"))) mustBe Some("false")
    }
    "resolve shallow float" in {
      resolveString(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-float"))) mustBe Some("1.0")
    }
    "resolve deep float" in {
      resolveString(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "float"))) mustBe Some("1.0")
    }
    "resolve shallow int" in {
      resolveString(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-int"))) mustBe Some("1")
    }
    "resolve deep int" in {
      resolveString(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "int"))) mustBe Some("1")
    }
    "resolve shallow double" in {
      resolveString(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-float"))) mustBe Some("1.0")
    }
    "resolve deep double" in {
      resolveString(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "double"))) mustBe Some("1.0")
    }

    "fail to resolve shallow null value" in {
      resolveString(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-null"))) mustBe None
    }
    "fail to resolve shallow object value" in {
      resolveString(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-object"))) mustBe None
    }
    "fail to resolve shallow array value" in {
      resolveString(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-array"))) mustBe None
    }
    "fail to resolve deep null value" in {
      resolveString(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "null"))) mustBe None
    }
    "fail to resolve deep object value" in {
      resolveString(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "object"))) mustBe None
    }
    "fail to resolve deep array value" in {
      resolveString(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "array"))) mustBe None
    }

    "fail to resolve missing shallow value" in {
      resolveString(ctxWithBody, Some(body), conf, BodyRef(Path("x"))) mustBe None
    }
    "fail to resolve missing deep value" in {
      resolveString(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "x"))) mustBe None
    }
  }

  "ValueResolver.resolveListOfStrings from body" should {
    val resolveListOfStrings = ValueResolver.resolveListOfStrings _

    "resolve shallow string" in {
      resolveListOfStrings(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-string"))) mustBe Some(List(""))
    }
    "resolve deep string" in {
      resolveListOfStrings(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "string"))) mustBe Some(List(""))
    }
    "resolve shallow boolean" in {
      resolveListOfStrings(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-boolean"))) mustBe Some(List("false"))
    }
    "resolve deep boolean" in {
      resolveListOfStrings(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "boolean"))) mustBe Some(List("false"))
    }
    "resolve shallow float" in {
      resolveListOfStrings(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-float"))) mustBe Some(List("1.0"))
    }
    "resolve deep float" in {
      resolveListOfStrings(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "float"))) mustBe Some(List("1.0"))
    }
    "resolve shallow int" in {
      resolveListOfStrings(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-int"))) mustBe Some(List("1"))
    }
    "resolve deep int" in {
      resolveListOfStrings(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "int"))) mustBe Some(List("1"))
    }
    "resolve shallow double" in {
      resolveListOfStrings(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-float"))) mustBe Some(List("1.0"))
    }
    "resolve deep double" in {
      resolveListOfStrings(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "double"))) mustBe Some(List("1.0"))
    }
    "resolve shallow array" in {
      resolveListOfStrings(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-array"))) mustBe Some(List())
    }
    "resolve deep array" in {
      resolveListOfStrings(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "array"))) mustBe Some(List())
    }

    "fail to resolve shallow null value" in {
      resolveListOfStrings(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-null"))) mustBe None
    }
    "fail to resolve shallow object value" in {
      resolveListOfStrings(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-object"))) mustBe None
    }
    "fail to resolve deep null value" in {
      resolveListOfStrings(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "null"))) mustBe None
    }
    "fail to resolve deep object value" in {
      resolveListOfStrings(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "object"))) mustBe None
    }

    "fail to resolve missing shallow value" in {
      resolveListOfStrings(ctxWithBody, Some(body), conf, BodyRef(Path("x"))) mustBe None
    }
    "fail to resolve missing deep value" in {
      resolveListOfStrings(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "x"))) mustBe None
    }
  }

  "ValueResolver.resolveJson from body" should {
    val resolveJson = ValueResolver.resolveJson _

    "resolve shallow string" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-string"))) mustBe Some(StringJsonValue(""))
    }
    "resolve deep string" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "string"))) mustBe Some(StringJsonValue(""))
    }
    "resolve deep string in array 0th elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[0]", "string-0"))) mustBe Some(StringJsonValue(""))
    }
    "resolve deep string in array 1st elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[1]", "string-1"))) mustBe Some(StringJsonValue(""))
    }
    "resolve string leaf array elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[3]"))) mustBe Some(StringJsonValue("string-array-elt"))
    }

    "resolve shallow object" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-object"))) mustBe Some(ObjectJsonValue(new JsonObject()))
    }
    "resolve deep object" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "object"))) mustBe Some(ObjectJsonValue(new JsonObject()))
    }
    "resolve deep object in array 0th elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[0]", "object-0"))) mustBe Some(ObjectJsonValue(new JsonObject()))
    }
    "resolve deep object in array 1st elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[1]", "object-1"))) mustBe Some(ObjectJsonValue(new JsonObject()))
    }

    "resolve shallow array" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-array"))) mustBe Some(ArrayJsonValue(new JsonArray()))
    }
    "resolve deep array" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "array"))) mustBe Some(ArrayJsonValue(new JsonArray()))
    }
    "resolve deep array in array 0th elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[0]", "array-0"))) mustBe Some(ArrayJsonValue(new JsonArray()))
    }
    "resolve deep array in array 1st elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[1]", "array-1"))) mustBe Some(ArrayJsonValue(new JsonArray()))
    }
    "resolve array leaf array elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[2]"))) mustBe Some(ArrayJsonValue(new JsonArray().add("deep-string-array-elt")))
    }
    "resolve string leaf array elt in nested array" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[2]", "[0]"))) mustBe Some(StringJsonValue("deep-string-array-elt"))
    }

    "resolve shallow boolean" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-boolean"))) mustBe Some(BooleanJsonValue(false))
    }
    "resolve deep boolean" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "boolean"))) mustBe Some(BooleanJsonValue(false))
    }
    "resolve deep boolean in array 0th elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[0]", "boolean-0"))) mustBe Some(BooleanJsonValue(false))
    }
    "resolve deep boolean in array 1st elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[1]", "boolean-1"))) mustBe Some(BooleanJsonValue(false))
    }
    "resolve boolean leaf array elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[4]"))) mustBe Some(BooleanJsonValue(false))
    }

    "resolve shallow float" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-float"))) mustBe Some(NumberJsonValue(1.0f))
    }
    "resolve deep float" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "float"))) mustBe Some(NumberJsonValue(1.0f))
    }
    "resolve deep float in array 0th elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[0]", "float-0"))) mustBe Some(NumberJsonValue(1.0f))
    }
    "resolve deep float in array 1st elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[1]", "float-1"))) mustBe Some(NumberJsonValue(1.0f))
    }
    "resolve float leaf array elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[5]"))) mustBe Some(NumberJsonValue(1.0f))
    }

    "resolve shallow double" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-double"))) mustBe Some(NumberJsonValue(1.0d))
    }
    "resolve deep double" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "double"))) mustBe Some(NumberJsonValue(1.0d))
    }
    "resolve deep double in array 0th elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[0]", "double-0"))) mustBe Some(NumberJsonValue(1.0d))
    }
    "resolve deep double in array 1st elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[1]", "double-1"))) mustBe Some(NumberJsonValue(1.0d))
    }
    "resolve double leaf array elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[6]"))) mustBe Some(NumberJsonValue(1.0d))
    }

    "resolve shallow int" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-int"))) mustBe Some(NumberJsonValue(1))
    }
    "resolve deep int" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "int"))) mustBe Some(NumberJsonValue(1))
    }
    "resolve deep int in array 0th elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[0]", "int-0"))) mustBe Some(NumberJsonValue(1))
    }
    "resolve deep int in array 1st elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[1]", "int-1"))) mustBe Some(NumberJsonValue(1))
    }
    "resolve int leaf array elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[7]"))) mustBe Some(NumberJsonValue(1))
    }

    "resolve shallow null" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("shallow-null"))) mustBe Some(NullJsonValue)
    }
    "resolve deep null" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep", "null"))) mustBe Some(NullJsonValue)
    }
    "resolve deep null in array 0th elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[0]", "null-0"))) mustBe Some(NullJsonValue)
    }
    "resolve deep null in array 1st elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[1]", "null-1"))) mustBe Some(NullJsonValue)
    }
    "resolve null leaf array elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[8]"))) mustBe Some(NullJsonValue)
    }

    "resolve missing array leaf elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[50]"))) mustBe None
    }
    "resolve missing array nested elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[50]", "name"))) mustBe None
    }
    "resolve missing negative array leaf elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[-1]"))) mustBe None
    }
    "resolve missing negative array nested elt" in {
      resolveJson(ctxWithBody, Some(body), conf, BodyRef(Path("deep-array", "[-1]", "name"))) mustBe None
    }
  }

  // resolve from authn

  "ValueResolver.resolveString from authn" should {

    "resolve shallow string" in {
      resolveString(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-string"))) mustBe Some("")
    }
    "resolve deep string" in {
      resolveString(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "string"))) mustBe Some("")
    }
    "resolve shallow boolean" in {
      resolveString(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-boolean"))) mustBe Some("false")
    }
    "resolve deep boolean" in {
      resolveString(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "boolean"))) mustBe Some("false")
    }
    "resolve shallow float" in {
      resolveString(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-float"))) mustBe Some("1")
    }
    "resolve deep float" in {
      resolveString(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "float"))) mustBe Some("1")
    }
    "resolve shallow int" in {
      resolveString(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-int"))) mustBe Some("1")
    }
    "resolve deep int" in {
      resolveString(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "int"))) mustBe Some("1")
    }
    "resolve shallow double" in {
      resolveString(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-float"))) mustBe Some("1")
    }
    "resolve deep double" in {
      resolveString(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "double"))) mustBe Some("1")
    }

    "fail to resolve shallow null value" in {
      resolveString(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-null"))) mustBe None
    }
    "fail to resolve shallow object value" in {
      resolveString(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-object"))) mustBe None
    }
    "fail to resolve shallow array value" in {
      resolveString(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-array"))) mustBe None
    }
    "fail to resolve deep null value" in {
      resolveString(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "null"))) mustBe None
    }
    "fail to resolve deep object value" in {
      resolveString(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "object"))) mustBe None
    }
    "fail to resolve deep array value" in {
      resolveString(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "array"))) mustBe None
    }

    "fail to resolve missing shallow value" in {
      resolveString(ctxWithAuthn, None, conf, AuthnRef(Path("x"))) mustBe None
    }
    "fail to resolve missing deep value" in {
      resolveString(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "x"))) mustBe None
    }
  }

  "ValueResolver.resolveListOfStrings from authn" should {
    val resolveListOfStrings = ValueResolver.resolveListOfStrings _

    "resolve shallow string" in {
      resolveListOfStrings(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-string"))) mustBe Some(List(""))
    }
    "resolve deep string" in {
      resolveListOfStrings(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "string"))) mustBe Some(List(""))
    }
    "resolve shallow boolean" in {
      resolveListOfStrings(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-boolean"))) mustBe Some(List("false"))
    }
    "resolve deep boolean" in {
      resolveListOfStrings(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "boolean"))) mustBe Some(List("false"))
    }
    "resolve shallow float" in {
      resolveListOfStrings(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-float"))) mustBe Some(List("1"))
    }
    "resolve deep float" in {
      resolveListOfStrings(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "float"))) mustBe Some(List("1"))
    }
    "resolve shallow int" in {
      resolveListOfStrings(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-int"))) mustBe Some(List("1"))
    }
    "resolve deep int" in {
      resolveListOfStrings(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "int"))) mustBe Some(List("1"))
    }
    "resolve shallow double" in {
      resolveListOfStrings(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-float"))) mustBe Some(List("1"))
    }
    "resolve deep double" in {
      resolveListOfStrings(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "double"))) mustBe Some(List("1"))
    }
    "resolve shallow array" in {
      resolveListOfStrings(ctxWithAuthn, Some(body), conf, AuthnRef(Path("shallow-array"))) mustBe Some(List())
    }
    "resolve deep array" in {
      resolveListOfStrings(ctxWithAuthn, Some(body), conf, AuthnRef(Path("deep", "array"))) mustBe Some(List())
    }

    "fail to resolve shallow null value" in {
      resolveListOfStrings(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-null"))) mustBe None
    }
    "fail to resolve shallow object value" in {
      resolveListOfStrings(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-object"))) mustBe None
    }
    "fail to resolve deep null value" in {
      resolveListOfStrings(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "null"))) mustBe None
    }
    "fail to resolve deep object value" in {
      resolveListOfStrings(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "object"))) mustBe None
    }

    "fail to resolve missing shallow value" in {
      resolveListOfStrings(ctxWithAuthn, None, conf, AuthnRef(Path("x"))) mustBe None
    }
    "fail to resolve missing deep value" in {
      resolveListOfStrings(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "x"))) mustBe None
    }
  }

  "ValueResolver.resolveJson from authn" should {
    val resolveJson = ValueResolver.resolveJson _

    "resolve shallow string" in {
      resolveJson(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-string"))) mustBe Some(StringJsonValue(""))
    }
    "resolve deep string" in {
      resolveJson(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "string"))) mustBe Some(StringJsonValue(""))
    }

    "resolve shallow object" in {
      resolveJson(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-object"))) mustBe Some(ObjectJsonValue(new JsonObject()))
    }
    "resolve deep object" in {
      resolveJson(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "object"))) mustBe Some(ObjectJsonValue(new JsonObject()))
    }

    "resolve shallow array" in {
      resolveJson(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-array"))) mustBe Some(ArrayJsonValue(new JsonArray()))
    }
    "resolve deep array" in {
      resolveJson(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "array"))) mustBe Some(ArrayJsonValue(new JsonArray()))
    }

    "resolve shallow boolean" in {
      resolveJson(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-boolean"))) mustBe Some(BooleanJsonValue(false))
    }
    "resolve deep boolean" in {
      resolveJson(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "boolean"))) mustBe Some(BooleanJsonValue(false))
    }

    "resolve shallow float" in {
      resolveJson(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-float"))) mustBe Some(NumberJsonValue(1.0f))
    }
    "resolve deep float" in {
      resolveJson(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "float"))) mustBe Some(NumberJsonValue(1.0f))
    }

    "resolve shallow double" in {
      resolveJson(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-double"))) mustBe Some(NumberJsonValue(1.0d))
    }
    "resolve deep double" in {
      resolveJson(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "double"))) mustBe Some(NumberJsonValue(1.0d))
    }

    "resolve shallow int" in {
      resolveJson(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-int"))) mustBe Some(NumberJsonValue(1))
    }
    "resolve deep int" in {
      resolveJson(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "int"))) mustBe Some(NumberJsonValue(1))
    }

    "resolve shallow null" in {
      resolveJson(ctxWithAuthn, None, conf, AuthnRef(Path("shallow-null"))) mustBe Some(NullJsonValue)
    }
    "resolve deep null" in {
      resolveJson(ctxWithAuthn, None, conf, AuthnRef(Path("deep", "null"))) mustBe Some(NullJsonValue)
    }
  }

  // resolve from pathParams

  "ValueResolver.resolveString from pathParams" should {

    "resolve existing param" in {
      resolveString(ctxWithPathParams, None, conf, PathParamRef("a")) mustBe Some("value")
    }

    "fail to resolve missing param" in {
      resolveString(ctxWithPathParams, None, conf, PathParamRef("x")) mustBe None
    }
  }

  "ValueResolver.resolveListOfStrings from pathParams" should {
    val resolveListOfStrings = ValueResolver.resolveListOfStrings _

    "resolve existing param" in {
      resolveListOfStrings(ctxWithPathParams, None, conf, PathParamRef("a")) mustBe Some(List("value"))
    }

    "fail to resolve missing param" in {
      resolveListOfStrings(ctxWithPathParams, None, conf, PathParamRef("x")) mustBe None
    }
  }

  "ValueResolver.resolveJson from pathParams" should {
    val resolveJson = ValueResolver.resolveJson _

    "resolve existing param" in {
      resolveJson(ctxWithPathParams, None, conf, PathParamRef("a")) mustBe Some(StringJsonValue("value"))
    }

    "fail to resolve missing param" in {
      resolveJson(ctxWithPathParams, None, conf, PathParamRef("x")) mustBe None
    }

    // resolve from headers

    "ValueResolver.resolveString from headers" should {

      "resolve first value of existing header if first-value ref type" in {
        resolveString(ctxWithHeaders, None, conf, HeaderRef("a", FirstHeaderRefType)) mustBe Some("x")
      }

      "resolve first value of existing header if all-values ref type" in { // if we reach this case, then someone mis-configured the plugin - trying to set a list of string to attribute that expects a string (e.g. path-param)
        resolveString(ctxWithHeaders, None, conf, HeaderRef("a", AllHeaderRefType)) mustBe Some("x")
      }

      "fail to resolve missing header" in {
        resolveString(ctxWithHeaders, None, conf, HeaderRef("x", FirstHeaderRefType)) mustBe None
      }
    }

    "ValueResolver.resolveListOfStrings from headers" should {
      val resolveListOfStrings = ValueResolver.resolveListOfStrings _

      "resolve first value of existing header if first-value ref type" in {
        resolveListOfStrings(ctxWithHeaders, None, conf, HeaderRef("a", FirstHeaderRefType)) mustBe Some(List("x"))
      }

      "resolve first value of existing header if all-values ref type" in {
        resolveListOfStrings(ctxWithHeaders, None, conf, HeaderRef("a", AllHeaderRefType)) mustBe Some(List("x", "y"))
      }

      "fail to resolve missing header" in {
        resolveListOfStrings(ctxWithHeaders, None, conf, HeaderRef("x", FirstHeaderRefType)) mustBe None
      }
    }

    "ValueResolver.resolveJson from headers" should {
      val resolveJson = ValueResolver.resolveJson _

      "resolve first value of existing header if first-value ref type" in {
        resolveJson(ctxWithHeaders, None, conf, HeaderRef("a", FirstHeaderRefType)) mustBe Some(StringJsonValue("x"))
      }

      "resolve first value of existing header if all-values ref type" in {
        resolveJson(ctxWithHeaders, None, conf, HeaderRef("a", AllHeaderRefType)) mustBe Some(ArrayJsonValue(new JsonArray().add("x").add("y")))
      }

      "fail to resolve missing header" in {
        resolveJson(ctxWithHeaders, None, conf, HeaderRef("x", FirstHeaderRefType)) mustBe None
      }
    }
  }
}
