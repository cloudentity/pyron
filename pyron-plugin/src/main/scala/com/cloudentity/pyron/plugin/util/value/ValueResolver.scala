package com.cloudentity.pyron.plugin.util.value

import com.cloudentity.pyron.domain.flow.{AuthnCtx, RequestCtx}
import com.cloudentity.pyron.plugin.util.value.PatternUtil.safePatternAndParams
import io.circe.Json
import io.vertx.core.json.{JsonArray, JsonObject}

import java.util.{List => JavaList, Map => JavaMap}
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.Try

object ValueResolver extends ValueResolver
trait ValueResolver {

  def resolveString(req: RequestCtx, bodyOpt: Option[JsonObject], valueOrRef: ValueOrRef): Option[String] =
    (valueOrRef: @unchecked) match { // no default case, fail fast
      case Value(value) => value.asString
      case HostRef => Option(req.original.host).filter(_.nonEmpty)
      case HostNameRef => resolveHostName(req)
      case HostPortRef => resolveHostPort(req)
      case SchemeRef => resolveScheme(req)
      case LocalHostRef => resolveLocalHost(req)
      case RemoteHostRef => resolveRemoteHost(req)
      case CookieRef(cookie) => resolveCookie(req, cookie)
      case BodyRef(path) => resolveBody(bodyOpt, path).flatMap(_.asString)
      case PathParamRef(param) => resolvePathParam(req, param)
      case QueryParamRef(param) => req.request.uri.query.get(param) // take first param value
      case HeaderRef(header, AllHeaderRefType) => req.request.headers.get(header) // take first header value
      case HeaderRef(header, FirstHeaderRefType) => req.request.headers.get(header) // take first header value
      case AuthnRef(path) => extractAuthnCtxAttribute(req.authnCtx, path).flatMap(_.asString)
    }

  def resolveString(req: RequestCtx, valueOrRef: ValueOrRef): Option[String] = valueOrRef match {
    case BodyRef(_) => resolveString(req, req.request.bodyOpt.flatMap(buf => Try(buf.toJsonObject).toOption), valueOrRef)
    case x          => resolveString(req, None, x)
  }

  def resolveListOfStrings(req: RequestCtx, bodyOpt: Option[JsonObject], valueOrRef: ValueOrRef): Option[List[String]] =
    (valueOrRef: @unchecked) match { // no default case, fail fast
      case Value(value)                          =>
        if (value.asObject.nonEmpty) circeJsonDynamicString(req, bodyOpt, value)
        else circeJsonToJsonValue(value).asListOfStrings
      case HostRef                               => resolveHost(req).map(List(_))
      case HostNameRef                           => resolveHostName(req).map(List(_))
      case HostPortRef                           => resolveHostPort(req).map(List(_))
      case SchemeRef                             => resolveScheme(req).map(List(_))
      case LocalHostRef                          => resolveLocalHost(req).map(List(_))
      case RemoteHostRef                         => resolveRemoteHost(req).map(List(_))
      case CookieRef(cookie)                     => resolveCookie(req, cookie).map(List(_))
      case BodyRef(path)                         => resolveBody(bodyOpt, path).flatMap(_.asListOfStrings)
      case PathParamRef(param)                   => resolvePathParam(req, param).map(List(_))
      case QueryParamRef(param)                  => req.request.uri.query.getValues(param)
      case HeaderRef(header, FirstHeaderRefType) => req.request.headers.get(header).map(List(_))
      case HeaderRef(header, AllHeaderRefType)   => req.request.headers.getValues(header)
      case AuthnRef(path)                        => extractAuthnCtxAttribute(req.authnCtx, path).flatMap(_.asListOfStrings)
    }

  def resolveJson(req: RequestCtx, bodyOpt: Option[JsonObject], valueOrRef: ValueOrRef): Option[JsonValue] =
    (valueOrRef: @unchecked) match { // no default case, fail fast
      case Value(value)                          => Some(circeJsonToJsonValue(value))
      case HostRef                               => Some(req.original.host).filter(_.nonEmpty).map(StringJsonValue)
      case RemoteHostRef                         => Some(req.original.remoteHost).filter(_.nonEmpty).map(StringJsonValue)
      case LocalHostRef                          => Some(req.original.localHost).filter(_.nonEmpty).map(StringJsonValue)
      case BodyRef(path)                         => resolveBody(bodyOpt, path)
      case PathParamRef(param)                   => resolvePathParam(req, param).map(StringJsonValue)
      case QueryParamRef(param)                  => req.request.uri.query.getValues(param) // array of all query param values
                                                      .map(v => ArrayJsonValue(new JsonArray(v.asJava)))
      case AuthnRef(path)                        => extractAuthnCtxAttribute(req.authnCtx, path)
      case HeaderRef(header, FirstHeaderRefType) => req.request.headers.get(header).map(StringJsonValue) // first header value
      case HeaderRef(header, AllHeaderRefType)   => req.request.headers.getValues(header) // array of all header values
                                                      .map(v => ArrayJsonValue(new JsonArray(v.asJava)))
    }

  private def resolveHost(req: RequestCtx): Option[String] =
    Option(req.original.host).filter(_.nonEmpty)

  private def resolveHostName(req: RequestCtx): Option[String] =
    Option(req.original.host).flatMap(_.split(':').headOption).filter(_.nonEmpty)

  private def resolveHostPort(req: RequestCtx): Option[String] =
    Option(req.original.host).flatMap(_.split(':').lastOption).filter(_.nonEmpty)

  private def resolveScheme(req: RequestCtx): Option[String] =
    Option(req.original.scheme).filter(_.nonEmpty)

  private def resolveLocalHost(req: RequestCtx): Option[String] =
    Option(req.original.localHost).filter(_.nonEmpty)

  private def resolveRemoteHost(req: RequestCtx): Option[String] =
    Option(req.original.remoteHost).filter(_.nonEmpty)

  private def resolveCookie(req: RequestCtx, cookie: String): Option[String] =
    req.original.cookies.get(cookie)

  private def resolveBody(bodyOpt: Option[JsonObject], path: Path): Option[JsonValue] =
    bodyOpt.flatMap(extractBodyAttribute(_, path))

  private def resolvePathParam(req: RequestCtx, param: String): Option[String] =
    req.request.uri.pathParams.value.get(param)

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

}
