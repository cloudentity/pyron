package com.cloudentity.pyron.plugin.util.value

import com.cloudentity.pyron.domain.flow.{AuthnCtx, RequestCtx}
import io.circe.Json
import io.vertx.core.json.{JsonArray, JsonObject}

import java.util.{List => JavaList, Map => JavaMap}
import scala.annotation.tailrec
import scala.util.Try


object ValueResolver extends ValueResolver
trait ValueResolver {
  def resolveString(req: RequestCtx, bodyOpt: Option[JsonObject], valueOrRef: ValueOrRef): Option[String] =
    valueOrRef match {
      case Value(value)        => value.asString
      case BodyRef(path)       => bodyOpt.flatMap(extractBodyAttribute(_, path)).flatMap(_.asString)
      case PathParamRef(param) => req.request.uri.pathParams.value.get(param)
      case AuthnRef(path)      => extractAuthnCtxAttribute(req.authnCtx, path).flatMap(_.asString)
      case HeaderRef(header, _) => req.request.headers.get(header) // always take first header value
    }

  def resolveString(req: RequestCtx, valueOrRef: ValueOrRef): Option[String] =
    valueOrRef match {
      case BodyRef(_) => resolveString(req, req.request.bodyOpt.flatMap(buf => Try(buf.toJsonObject).toOption), valueOrRef)
      case x          => resolveString(req, None, x)
    }

  def resolveListOfStrings(req: RequestCtx, bodyOpt: Option[JsonObject], valueOrRef: ValueOrRef): Option[List[String]] =
    valueOrRef match {
      case Value(value)                          =>
        if (value.asObject.nonEmpty) circeJsonDynString(req, bodyOpt, value).map(List(_))
        else circeJsonToJsonValue(value).asListOfStrings
      case BodyRef(path)                         => bodyOpt.flatMap(extractBodyAttribute(_, path)).flatMap(_.asListOfStrings)
      case PathParamRef(param)                   => req.request.uri.pathParams.value.get(param).map(List(_))
      case AuthnRef(path)                        => extractAuthnCtxAttribute(req.authnCtx, path).flatMap(_.asListOfStrings)
      case HeaderRef(header, FirstHeaderRefType) => req.request.headers.get(header).map(List(_))
      case HeaderRef(header, AllHeaderRefType)   => req.request.headers.getValues(header)
    }

  def resolveJson(req: RequestCtx, bodyOpt: Option[JsonObject], valueOrRef: ValueOrRef): Option[JsonValue] =
    valueOrRef match {
      case Value(value)                          => Some(circeJsonToJsonValue(value))
      case BodyRef(path)                         => bodyOpt.flatMap(extractBodyAttribute(_, path))
      case PathParamRef(param)                   => req.request.uri.pathParams.value.get(param).map(StringJsonValue)
      case AuthnRef(path)                        => extractAuthnCtxAttribute(req.authnCtx, path)
      case HeaderRef(header, FirstHeaderRefType) => req.request.headers.get(header).map(StringJsonValue) // returning String with first header value
      case HeaderRef(header, AllHeaderRefType)   => req.request.headers.getValues(header).map(_.foldLeft(new JsonArray())(_.add(_))).map(ArrayJsonValue) // returning JsonArray with all header values
    }

  @tailrec
  private def extractBodyAttribute(body: JsonObject, path: Path): Option[JsonValue] = {
    path.value match {
      case Nil => None
      case key :: Nil => extractLeafBodyAttribute(body, key)
      case key :: tail =>
        val value = body.getValue(key)
        if (value == null) {
          None
        } else value match {
          case v: JsonObject => extractBodyAttribute(v, Path(tail))
          case v: JavaMap[_, _] => extractBodyAttribute(new JsonObject(v.asInstanceOf[JavaMap[String, Object]]), Path(tail))
          case _ => None
        }
    }
  }

  private def extractLeafBodyAttribute(body: JsonObject, key: String): Option[JsonValue] = {
    val value = body.getValue(key)
    if (value == null) {
      Some(NullJsonValue)
    } else if (value == true || value == false) {
      Some(BooleanJsonValue(value.asInstanceOf[Boolean]))
    } else value match {
      case v: String => Some(StringJsonValue(v))
      case v: Number => Some(NumberJsonValue(v))
      case v: JsonObject => Some(ObjectJsonValue(v))
      case v: JsonArray => Some(ArrayJsonValue(v))
      case v: JavaList[_] => Some(ArrayJsonValue(new JsonArray(v)))
      case v: JavaMap[_, _] => Some(ObjectJsonValue(new JsonObject(v.asInstanceOf[JavaMap[String, Object]])))
      case _ => None
    }
  }

  private def extractAuthnCtxAttribute(authn: AuthnCtx, path: Path): Option[JsonValue] =
    path.value match {
      case Nil => None
      case key :: Nil =>
        authn.get(key).map(circeJsonToJsonValue)
      case key :: tail => for {
        value <- authn.get(key)
        obj <- value.asObject
        attr <- extractAuthnCtxAttribute(AuthnCtx(obj.toMap), Path(tail))
      } yield attr
    }

  private def circeJsonDynString(req: RequestCtx, bodyOpt: Option[JsonObject], json: Json): Option[String] = {
    for {
      patternOpt <- json.hcursor.downField("pattern").focus
      patternStr <- patternOpt.asString
      outputOpt <- json.hcursor.downField("output").focus
      outputStr <- outputOpt.asString
      pathOpt <- json.hcursor.downField("path").focus
      valOrRef <- pathOpt.as[ValueOrRef].toOption

      regexGroupNames = """\{(\w+)}""".r.findAllMatchIn(patternStr).map(_.group(1))
      regexWithParams = safeRegexWithParams(patternStr)

      jsonValue <- resolveJson(req, bodyOpt, valOrRef)
      candidates <- jsonValue.asListOfStrings
      found <- candidates.find(v => v.matches(regexWithParams))
      matched <- regexWithParams.r.findFirstMatchIn(found)
      keyvals = regexGroupNames.toList.zip(matched.subgroups)
    } yield keyvals.foldLeft(outputStr) {
      case (output, (key, value)) => output.replaceAllLiterally(s"{$key}", value)
    }
  }

  private def safeRegexWithParams(pattern: String): String = {
    val paramRestoreRegex = """\\\{(\w+)\\}""".r
    val sanitizerRegex = """([.*+?^${}()|\[\]\\])""".r
    val regexSanitized = sanitizerRegex.replaceAllIn(pattern, v => """\\\""" + v.group(1))
    paramRestoreRegex.replaceAllIn(s"^$regexSanitized$$", m => s"(?<${m.group(1)}>.+)")
  }

  private def circeJsonToJsonValue(json: Json): JsonValue = {
    import io.circe.syntax._
    json.fold[JsonValue](
      NullJsonValue,
      bool   => BooleanJsonValue(bool),
      num    => NumberJsonValue(num.toInt.getOrElse(num.toDouble).asInstanceOf[Number]),
      string => StringJsonValue(string),
      array  => ArrayJsonValue(new JsonArray(array.asJson.noSpaces)),
      obj    => ObjectJsonValue(new JsonObject(obj.asJson.noSpaces))
    )
  }

}
