package com.cloudentity.pyron.plugin.util.value

import com.cloudentity.pyron.domain.flow.{AuthnCtx, RequestCtx}
import io.circe.Json
import io.vertx.core.json.{JsonArray, JsonObject}

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
      case Value(value)                          => circeJsonToJsonValue(value, bodyOpt)
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
  private def extractBodyAttribute(body: JsonObject, path: Path): Option[JsonValue] =
    path.value match {
      case key :: Nil =>
        val value = body.getValue(key)

        if (value == null)                                Some(NullJsonValue)
        else if (value.isInstanceOf[JsonObject])          Some(ObjectJsonValue(value.asInstanceOf[JsonObject]))
        else if (value.isInstanceOf[java.util.Map[_, _]]) Some(ObjectJsonValue(new JsonObject(value.asInstanceOf[java.util.Map[String, Object]])))
        else if (value.isInstanceOf[JsonArray])           Some(ArrayJsonValue(value.asInstanceOf[JsonArray]))
        else if (value.isInstanceOf[java.util.List[_]])   Some(ArrayJsonValue(new JsonArray(value.asInstanceOf[java.util.List[_]])))
        else if (value.isInstanceOf[String])              Some(StringJsonValue(value.asInstanceOf[String]))
        else if (value.isInstanceOf[Number])              Some(NumberJsonValue(value.asInstanceOf[Number]))
        else if (value.isInstanceOf[Boolean])             Some(BooleanJsonValue(value.asInstanceOf[Boolean]))
        else                                              None

      case key :: tail =>
        val value = body.getValue(key)

        if (value == null) None
        else if (value.isInstanceOf[JsonObject]) extractBodyAttribute(value.asInstanceOf[JsonObject], Path(tail))
        else if (value.isInstanceOf[java.util.Map[_, _]]) extractBodyAttribute(new io.vertx.core.json.JsonObject(value.asInstanceOf[java.util.Map[String, Object]]), Path(tail))
        else None
      case Nil => None
    }


  import io.circe.syntax._
  private def extractAuthnCtxAttribute(authn: AuthnCtx, path: Path): Option[JsonValue] =
    path.value match {
      case key :: Nil =>
        authn.get(key).map(circeJsonToJsonValue)

      case key :: tail =>
        authn.get(key) match {
          case Some(json) =>
            json.asObject.flatMap(obj => extractAuthnCtxAttribute(AuthnCtx(obj.toMap), Path(tail)))
          case None =>
            None
        }
      case Nil => None
    }

  private def circeJsonToJsonValue(json: Json, bodyOpt: Option[JsonObject]): Option[List[String]] = {
    import scala.collection.JavaConverters._
    val value = for {
      body <- bodyOpt
      prefixOpt <- json.hcursor.downField("prefix").focus
      prefix <- prefixOpt.asString
      pathOpt <- json.hcursor.downField("findAt").focus
      path <- pathOpt.asString
      found <- body.getJsonArray(path).getList.asScala.toList.find(_.toString.contains(prefix))
    } yield found.toString.substring(prefix.length)
    value.map(List(_))
  }

  private def circeJsonToJsonValue(json: Json) =
    json.fold[JsonValue](
      NullJsonValue,
      bool   => BooleanJsonValue(bool),
      num    => NumberJsonValue(num.toInt.getOrElse(num.toDouble).asInstanceOf[Number]),
      string => StringJsonValue(string),
      array  => ArrayJsonValue(new JsonArray(array.asJson.noSpaces)),
      obj    => ObjectJsonValue(new JsonObject(obj.asJson.noSpaces))
    )
}
