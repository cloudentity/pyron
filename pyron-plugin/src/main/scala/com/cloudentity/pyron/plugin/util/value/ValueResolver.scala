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
        if (value.asObject.nonEmpty) circeJsonDynamicString(req, bodyOpt, value)
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

  private def circeJsonDynamicString(req: RequestCtx, bodyOpt: Option[JsonObject], json: Json): Option[List[String]] = {
    for {
      patternOpt <- json.hcursor.downField("pattern").focus
      patternStr <- patternOpt.asString
      outputOpt <- json.hcursor.downField("output").focus
      outputStr <- outputOpt.asString
      pathOpt <- json.hcursor.downField("path").focus
      valOrRef <- pathOpt.as[ValueOrRef].toOption
      (safePattern, paramNames) = safePatternAndParams(patternStr)
      jsonValue <- resolveJson(req, bodyOpt, valOrRef)
      candidates <- jsonValue.asListOfStrings
    } yield candidates.flatMap(safePattern.findFirstMatchIn)
      .map(matched => paramNames.foldLeft(outputStr)((output, param) => {
        output.replace(s"{$param}", matched.group(param))
      }))
  }

  def safePatternAndParams(pattern: String): (Regex, List[String]) = {
    @tailrec
    def loop(sliceItAt: List[Int], isParamName: Boolean, slicesAcc: List[String], paramsAcc: List[String]): (Regex, List[String]) =
      sliceItAt match {
        case end :: start :: tail =>
          val (slices, params) = if (isParamName) {
            val paramDef = pattern.slice(start, end)
            val (paramName, paramMatch) = getParamMatch(paramDef)
            (paramMatch :: slicesAcc, paramName :: paramsAcc)
          } else {
            val otherThanParamName = pattern.slice(start, end)
            (escapeSymbols(normalizeParens(otherThanParamName)) :: slicesAcc, paramsAcc)
          }
          // 0-th and each even slice is non-param
          loop(start :: tail, !isParamName, slices, params)
        case _ => (("^" + slicesAcc.mkString + "$").r, paramsAcc)
      }

    loop(getSliceItAt(pattern), isParamName = false, slicesAcc = Nil, paramsAcc = Nil)
  }

  private def getSliceItAt(pattern: String): List[Int] = {
    // Find indexes delimiting non-param and param-def slices of the pattern
    val paramDef = """\{([a-zA-Z][a-zA-Z0-9]*(:?_[0-9]+){0,2}?)}"""
    val re = if (pattern.contains("{{")) {
      // We allow {{ and }} to match literal { and }, which makes finding params harder
      ("""(?:\G|[^{])(?:\{\{)*""" + paramDef).r
    } else {
      // Patterns without {{ and }} are likely much more common and simpler matcher will do
      paramDef.r
    }
    pattern.length :: re.findAllMatchIn(pattern)
      // Param def is captured into group(1)
      .foldLeft(List(0)) { case (acc, m) =>
        val openingCurlyBraceAt = m.end - m.group(1).length - 1
        val closingCurlyBraceAt = m.end - 1
        closingCurlyBraceAt :: openingCurlyBraceAt :: acc
      }
  }

  private def getParamMatch(paramDef: String): (String, String) = {
    paramDef.split('_').toList match {
      case paramName :: Nil => (paramName, s"(?<$paramName>.+)")
      case paramName :: matchSize :: Nil => (paramName, s"(?<$paramName>.{$matchSize})")
      case paramName :: minSize :: maxSize :: Nil =>
        assert(minSize < maxSize, s"Minimum must be smaller than maximum capture size but was: ($minSize, $maxSize)")
        (paramName, s"(?<$paramName>.{$minSize,$maxSize})")
      case _ => throw new Exception(s"Malformed param definition: [$paramDef]")
    }
  }

  private def escapeSymbols(normalizedParensSlice: String): String = {
    val escapeSymbolsRegex = """[.*+?^$()|{\[\\]""".r
    escapeSymbolsRegex.replaceAllIn(normalizedParensSlice, v => """\\\""" + v)
  }

  private def normalizeParens(nonParamSlice: String): String = {
    // this match will un-double all {{ and drop single/odd { parens
    val normalizeParensRegex = """([{}])(?<dualParen>\1?)""".r
    normalizeParensRegex.replaceAllIn(nonParamSlice, _.group("dualParen"))
  }
}
