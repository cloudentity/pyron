package com.cloudentity.pyron.plugin.util.value

import com.cloudentity.pyron.domain.flow.{AuthnCtx, RequestCtx, ResponseCtx}
import com.cloudentity.pyron.domain.http
import com.cloudentity.pyron.domain.http.{OriginalRequest, TargetRequest}
import com.cloudentity.pyron.plugin.util.value.PatternUtil.safePatternAndParams
import com.cloudentity.pyron.plugin.util.value.ValueResolver.ResolveCtx
import io.circe.Json
import io.vertx.core.json.{JsonArray, JsonObject}

import java.util.{List => JavaList, Map => JavaMap}
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.language.implicitConversions
import scala.util.Try

object ValueResolver {

  case class ResolveCtx(origReq: OriginalRequest,
                        tgtReq: TargetRequest,
                        authnCtx: AuthnCtx,
                        headers: http.Headers)

  implicit def requestCtx_to_resolveCtx(ctx: RequestCtx): ResolveCtx =
    ResolveCtx(ctx.originalRequest, ctx.targetRequest, ctx.authnCtx, ctx.targetRequest.headers)

  implicit def responseCtx_to_resolveCtx(ctx: ResponseCtx): ResolveCtx =
    ResolveCtx(ctx.originalRequest, ctx.targetRequest, ctx.authnCtx, ctx.response.headers)

  def resolveString(ctx: ResolveCtx,
                    bodyOpt: Option[JsonObject],
                    confValues: JsonObject,
                    valueOrRef: ValueOrRef): Option[String] = {
    valueOrRef match {
      case Value(value)                          => value.asString
      case HostRef                               => resolveHost(ctx.origReq)
      case HostNameRef                           => resolveHostName(ctx.origReq)
      case HostPortRef                           => resolveHostPort(ctx.origReq)
      case SchemeRef                             => resolveScheme(ctx.origReq)
      case LocalHostRef                          => resolveLocalHost(ctx.origReq)
      case RemoteHostRef                         => resolveRemoteHost(ctx.origReq)
      case CookieRef(cookie)                     => resolveCookie(ctx.origReq, cookie)
      case BodyRef(path)                         => resolveBody(bodyOpt, path).flatMap(_.asString)
      case PathParamRef(param)                   => resolvePathParam(ctx.tgtReq, param)
      case QueryParamRef(param)                  => ctx.tgtReq.uri.query.get(param)
      case HeaderRef(header, FirstHeaderRefType) => ctx.headers.get(header)
      case HeaderRef(header, AllHeaderRefType)   =>  ctx.headers.get(header)
      case AuthnRef(path)                        => extractAuthnCtxAttribute(ctx.authnCtx, path).flatMap(_.asString)
      case ConfRef(path)                         => extractJsonObjectAttribute(confValues, path).flatMap(_.asString)
    }
  }

  def resolveString(ctx: ResolveCtx, confValues: JsonObject, valueOrRef: ValueOrRef): Option[String] =
    valueOrRef match {
      case BodyRef(_) =>
        val bodyOpt = ctx.tgtReq.bodyOpt.flatMap(buf => Try(buf.toJsonObject).toOption)
        resolveString(ctx, bodyOpt, confValues, valueOrRef)
      case x => resolveString(ctx, None, confValues, x)
    }


  def resolveListOfStrings(ctx: ResolveCtx,
                           bodyOpt: Option[JsonObject],
                           confValues: JsonObject,
                           valueOrRef: ValueOrRef): Option[List[String]] = {
    valueOrRef match {
      case Value(value) =>
        if (value.asObject.nonEmpty) circeJsonDynamicString(ctx, bodyOpt, confValues, value)
        else circeJsonToJsonValue(value).asListOfStrings
      case HostRef                               => resolveHost(ctx.origReq).map(List(_))
      case HostNameRef                           => resolveHostName(ctx.origReq).map(List(_))
      case HostPortRef                           => resolveHostPort(ctx.origReq).map(List(_))
      case SchemeRef                             => resolveScheme(ctx.origReq).map(List(_))
      case LocalHostRef                          => resolveLocalHost(ctx.origReq).map(List(_))
      case RemoteHostRef                         => resolveRemoteHost(ctx.origReq).map(List(_))
      case CookieRef(cookie)                     => resolveCookie(ctx.origReq, cookie).map(List(_))
      case BodyRef(path)                         => resolveBody(bodyOpt, path).flatMap(_.asListOfStrings)
      case PathParamRef(param)                   => resolvePathParam(ctx.tgtReq, param).map(List(_))
      case QueryParamRef(param)                  => ctx.tgtReq.uri.query.getValues(param)
      case HeaderRef(header, FirstHeaderRefType) => ctx.headers.get(header).map(List(_))
      case HeaderRef(header, AllHeaderRefType)   => ctx.headers.getValues(header)
      case AuthnRef(path)                        => extractAuthnCtxAttribute(ctx.authnCtx, path).flatMap(_.asListOfStrings)
      case ConfRef(path)                         => extractJsonObjectAttribute(confValues, path).flatMap(_.asListOfStrings)
    }
  }

  def resolveJson(ctx: ResolveCtx,
                  bodyOpt: Option[JsonObject],
                  confValues: JsonObject,
                  valueOrRef: ValueOrRef): Option[JsonValue] = {
    valueOrRef match {
      case Value(value)                          => Some(circeJsonToJsonValue(value))
      case HostRef                               => resolveHost(ctx.origReq).map(StringJsonValue)
      case HostNameRef                           => resolveHostName(ctx.origReq).map(StringJsonValue)
      case HostPortRef                           => resolveHostPort(ctx.origReq).map(StringJsonValue)
      case SchemeRef                             => resolveScheme(ctx.origReq).map(StringJsonValue)
      case RemoteHostRef                         => resolveRemoteHost(ctx.origReq).map(StringJsonValue)
      case LocalHostRef                          => resolveLocalHost(ctx.origReq).map(StringJsonValue)
      case BodyRef(path)                         => resolveBody(bodyOpt, path)
      case PathParamRef(param)                   => resolvePathParam(ctx.tgtReq, param).map(StringJsonValue)
      case QueryParamRef(param)                  => resolveQueryParam(ctx.tgtReq, param) // array of all query param values
                                                      .map(v => ArrayJsonValue(new JsonArray(v.asJava)))
      case CookieRef(cookie)                     => resolveCookie(ctx.origReq, cookie).map(StringJsonValue)
      case AuthnRef(path)                        => extractAuthnCtxAttribute(ctx.authnCtx, path)
      case HeaderRef(header, FirstHeaderRefType) => ctx.headers.get(header).map(StringJsonValue) // first header value
      case HeaderRef(header, AllHeaderRefType)   => ctx.headers.getValues(header) // array of all header values
                                                      .map(v => ArrayJsonValue(new JsonArray(v.asJava)))
      case ConfRef(path)                         => extractJsonObjectAttribute(confValues, path)
    }
  }

  private def resolveBody(bodyOpt: Option[JsonObject], path: Path): Option[JsonValue] =
    bodyOpt.flatMap(extractJsonObjectAttribute(_, path))

  private def resolveHost(origReq: OriginalRequest): Option[String] =
    Option(origReq.host).filter(_.nonEmpty)

  private def resolveHostName(origReq: OriginalRequest): Option[String] =
    Option(origReq.host).flatMap(_.split(':').headOption).filter(_.nonEmpty)

  private def resolveHostPort(origReq: OriginalRequest): Option[String] =
    Option(origReq.host).flatMap(_.split(':').lastOption).filter(_.nonEmpty)

  private def resolveLocalHost(origReq: OriginalRequest): Option[String] =
    Option(origReq.localHost).filter(_.nonEmpty)

  private def resolveRemoteHost(origReq: OriginalRequest): Option[String] =
    Option(origReq.remoteHost).filter(_.nonEmpty)

  private def resolveScheme(origReq: OriginalRequest): Option[String] =
    Option(origReq.scheme).filter(_.nonEmpty)

  private def resolveCookie(origReq: OriginalRequest, cookie: String): Option[String] =
    origReq.cookies.get(cookie).map(_.value)

  private def resolvePathParam(tgtReq: TargetRequest, param: String): Option[String] =
    tgtReq.uri.pathParams.value.get(param)

  private def resolveQueryParam(tgtReq: TargetRequest, param: String): Option[List[String]] =
    tgtReq.uri.query.getValues(param)


  @tailrec
  private def extractJsonObjectAttribute(obj: JsonObject, path: Path): Option[JsonValue] = {
    path.value match {
      case Nil => None
      case key :: Nil => extractLeafBodyAttribute(obj, key)
      case key :: tail =>
        val value = obj.getValue(key)
        if (value == null) {
          None
        } else value match {
          case v: JsonObject => extractJsonObjectAttribute(v, Path(tail))
          case v: JavaMap[_, _] => extractJsonObjectAttribute(new JsonObject(v.asInstanceOf[JavaMap[String, Object]]), Path(tail))
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

  private def circeJsonDynamicString(ctx: ResolveCtx, bodyOpt: Option[JsonObject], confValues: JsonObject, json: Json): Option[List[String]] = {
    for {
      patternOpt <- json.hcursor.downField("pattern").focus
      patternStr <- patternOpt.asString
      outputOpt <- json.hcursor.downField("output").focus
      outputStr <- outputOpt.asString
      pathOpt <- json.hcursor.downField("path").focus
      valOrRef <- pathOpt.as[ValueOrRef].toOption
      (safePattern, paramNames) = safePatternAndParams(patternStr)
      jsonValue <- resolveJson(ctx, bodyOpt, confValues, valOrRef)
      candidates <- jsonValue.asListOfStrings
    } yield candidates.flatMap(safePattern.findFirstMatchIn)
      .map(matched => paramNames.foldLeft(outputStr)((output, param) => {
        output.replace(s"{$param}", matched.group(param))
      }))
  }
}