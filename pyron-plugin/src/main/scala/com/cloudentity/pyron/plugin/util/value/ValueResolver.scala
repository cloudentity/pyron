package com.cloudentity.pyron.plugin.util.value

import com.cloudentity.pyron.domain.flow.{AuthnCtx, RequestCtx}
import io.circe.Json
import io.vertx.core.json.{JsonArray, JsonObject}

import java.util.{List => JavaList, Map => JavaMap}
import scala.annotation.tailrec
import scala.util.Try
import scala.util.matching.Regex


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
        if (value.asObject.nonEmpty) circeJsonDynamicString(req, bodyOpt, value).map(List(_))
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

  private def circeJsonDynamicString(req: RequestCtx, bodyOpt: Option[JsonObject], json: Json): Option[String] = {
    for {
      patternOpt <- json.hcursor.downField("pattern").focus
      patternStr <- patternOpt.asString
      outputOpt <- json.hcursor.downField("output").focus
      outputStr <- outputOpt.asString
      pathOpt <- json.hcursor.downField("path").focus
      valOrRef <- pathOpt.as[ValueOrRef].toOption

      (safePattern, paramNames) = makeSafePatternAndParamList(patternStr)

      jsonValue <- resolveJson(req, bodyOpt, valOrRef)
      candidates <- jsonValue.asListOfStrings
      matched <- candidates.view.map(safePattern.findFirstMatchIn).collectFirst { case Some(found) => found }
      keyvals = paramNames.zip(matched.subgroups)
    } yield keyvals.foldLeft(outputStr) {
      case (output, (key, value)) => output.replaceAllLiterally(s"{$key}", value)
    }
  }

  private def makeSafePatternAndParamList(pattern: String): (Regex, List[String]) = {
    val (paramNames, paramSlices) = findParamSlices(pattern)
    val safePattern = ("" :: paramNames).zip(makeNonParamSlices(pattern, paramSlices))
      .map { case (paramName: String, followingSlice: String) =>
        if (paramName.nonEmpty) s"(?<$paramName>.+)" + followingSlice else followingSlice
      }.mkString
    (safePattern.r, paramNames)
  }

  private def findParamSlices(pattern: String): (List[String], List[(Int, Int)]) = {
    if (pattern.contains("{{")) {
      // We allow {{ and }} to match literal { and }, which makes finding params harder
      // capture group 1 contains possible occurrences of {{ before opening paren { of param definition
      // capture group 2 contains param name without surrounding parens
      """((?:\G|[^{])(?:\{\{)*)\{(\w+)}""".r.findAllMatchIn(pattern)
        .map(m => m.group(2) -> (m.start + m.group(1).length, m.end)).toList.unzip
    } else {
      // Patterns without {{ and }} are likely much more common and simpler matcher will do
      // capture group 1 contains param name without surrounding parens
      """\{(\w+)}""".r.findAllMatchIn(pattern)
        .map(m => m.group(1) -> (m.start, m.end)).toList.unzip
    }
  }

  private def makeNonParamSlices(pattern: String, paramSlices: List[(Int, Int)]): List[String] = {
    val nonParamSlices: List[Int] = pattern.length :: paramSlices
      .foldLeft(List(0)) { case (acc, (start, end)) => end :: start :: acc }
    nonParamSlices.sliding(2, 2).foldLeft(List[String]()) {
      (acc, slice) => {
        val (start, end) = (slice.tail.head, slice.head)
        escapeSymbols(normalizeParens(pattern.slice(start, end))) :: acc
      }
    }
  }

  private def escapeSymbols(normalizedParensSlice: String): String = {
    val escapeSymbolsRegex = """[.*+?^$()|{\[\\]""".r
    escapeSymbolsRegex.replaceAllIn(normalizedParensSlice, v => """\\\""" + v)
  }

  private def normalizeParens(nonParamSlice: String): String = {
    // this match will un-double all {{ and drop, single/odd { parens which are invalid
    // since they could only precede param names, and nonParamSlice contains no params
    val normalizeParensRegex = """([{}])(?<dualParen>\1?)""".r
    normalizeParensRegex.replaceAllIn(nonParamSlice, _.group("dualParen"))
  }
}
