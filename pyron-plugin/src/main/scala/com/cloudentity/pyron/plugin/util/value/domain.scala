package com.cloudentity.pyron.plugin.util.value

import io.circe.{Decoder, DecodingFailure, HCursor, Json, KeyDecoder}
import io.vertx.core.json.{JsonArray, JsonObject}

import scala.collection.JavaConverters._

sealed trait ValueOrRef
case class Value(value: Json) extends ValueOrRef
sealed trait RefType extends ValueOrRef
case class RequestBodyRef(path: Path) extends RefType
case class ResponseBodyRef(path: Path) extends RefType
case class PathParamRef(param: String) extends RefType
case class QueryParamRef(param: String) extends RefType
case class CookieRef(cookie: String) extends RefType
case class AuthnRef(path: Path) extends RefType
case class RequestHeaderRef(header: String, typ: HeaderRefType) extends RefType
case class ResponseHeaderRef(header: String, typ: HeaderRefType) extends RefType
case class ConfRef(path: Path) extends RefType
case class PropertiesRef(path: Path) extends RefType

sealed trait HeaderRefType
case object FirstHeaderRefType extends HeaderRefType
case object AllHeaderRefType extends HeaderRefType

case object HostRef extends RefType
case object HostNameRef extends RefType

case object HostPortRef extends RefType
case object SchemeRef extends RefType

case object LocalHostRef extends RefType
case object RemoteHostRef extends RefType
case object HttpStatusRef extends RefType

object ValueOrRef {

  implicit val ValueOrRefDecoder: Decoder[ValueOrRef] = Decoder.decodeJson.emap { json =>
    json.asString.filter(_.startsWith("$")).map {
      decodeReference
    } getOrElse Right(Value(json))
  }

  private def decodeReference(string: String): Either[String, RefType] =
    string.drop(1).split('.').toList match {
      case Nil => Left("reference cannot be empty")
      case "req" :: refType :: path => decodeRefWithPath(s"req.$refType", path).map(Right(_)).getOrElse(Left(s"invalid reference: $string"))
      case "resp" :: refType :: path => decodeRefWithPath(s"resp.$refType", path).map(Right(_)).getOrElse(Left(s"invalid reference: $string"))
      case refType :: path => decodeRefWithPath(refType, path)
        .orElse(if (path.isEmpty) decodeRefWithNoPath(refType) else None)
        .map(Right(_)).getOrElse(Left(s"invalid reference: $string"))
    }

  private def decodeRefWithNoPath(refType: String): Option[RefType] =
    refType match {
      case "scheme" => Some(SchemeRef)
      case "host" => Some(HostRef)
      case "hostName" => Some(HostNameRef)
      case "hostPort" => Some(HostPortRef)
      case "localHost" => Some(LocalHostRef)
      case "remoteHost" => Some(RemoteHostRef)
      case _ => None
    }

  private def decodeRefWithPath(refType: String, path: List[String]): Option[RefType] =
    refType match {
      case "authn" => Some(AuthnRef(Path(path)))
      case "body" => Some(RequestBodyRef(Path(path)))
      case "req.body" => Some(RequestBodyRef(Path(path)))
      case "resp.body" => Some(ResponseBodyRef(Path(path)))
      case "resp.status" => Some(HttpStatusRef)
      case "cookies" => Some(CookieRef(path.mkString(".")))
      case "headers" => decodeHeadersRef(path, RequestHeaderRef.apply _)
      case "req.headers" => decodeHeadersRef(path, RequestHeaderRef.apply _)
      case "resp.headers" => decodeHeadersRef(path, ResponseHeaderRef.apply _)
      case "pathParams" => Some(PathParamRef(path.mkString(".")))
      case "queryParams" => Some(QueryParamRef(path.mkString(".")))
      case "props" => Some(PropertiesRef(Path(path)))
      case "conf" => Some(ConfRef(Path(path)))
      case _ => None
    }

  private def decodeHeadersRef[A](path: List[String], f: (String, HeaderRefType) => A): Option[A] = Some {
    path match {
      case header :: "*" :: Nil =>
        f(header, AllHeaderRefType)
      case path =>
        f(path.mkString("."), FirstHeaderRefType)
    }
  }
}

case class Path(value: List[String])
object Path {
  implicit val PathKeyDecoder: KeyDecoder[Path] = key => Some(Path(key.split('.').toList))

  implicit val PathDecoder: Decoder[Path] = Decoder.decodeString.emap(key =>
    Right(Path(key.split('.').toList))
  )

  def apply(xs: String*): Path = Path(xs.toList)
}

sealed trait ValueOrRemove
object ValueOrRemove {
  implicit val valueOrRemoveDecoder: Decoder[ValueOrRemove] = (c: HCursor) => for {
    removeOpt <- c.get[Option[Boolean]]("remove")
    valueOpt <- c.get[Option[Json]]("value")
    result <- (removeOpt, valueOpt) match {
      case (Some(true), None) => Right(RemoveContent)
      case (None, Some(json)) => Right(ValueContent(json))
      case _ => Left(DecodingFailure("""Need to set either {"remove": true} or {"value": <someJsonValue>} for config""", Nil))
    }
  } yield result
}
case object RemoveContent extends ValueOrRemove
case class ValueContent(value: Json) extends ValueOrRemove

case class SetWithDefaultEntry(sourcePath: ValueOrRef, ifNull: ValueOrRemove, ifAbsent: ValueOrRemove) {
  def resolveValue(foundValue: Option[JsonValue]): JsonValueIgnoreNullIfDefault = {
    val jsonValue = (foundValue, ifNull, ifAbsent) match {
      case (Some(NullJsonValue), RemoveContent, _) => None
      case (Some(NullJsonValue), ValueContent(defaultValue), _) => Some(JsonValue(defaultValue))
      case (Some(value), _, _) => Some(value)
      case (None, _, RemoveContent) => None
      case (None, _, ValueContent(defaultValue)) => Some(JsonValue(defaultValue))
    }
    JsonValueIgnoreNullIfDefault(jsonValue, ignoreNullIfAbsent = true)
  }
}
object SetWithDefaultEntry {
  val defaultIfNull = ValueContent(Json.Null)
  val defaultIfAbsent = RemoveContent
  implicit val setWithDefaultEntryDecoder: Decoder[SetWithDefaultEntry] = (c: HCursor) => for {
    sourcePath <- c.get[ValueOrRef]("sourcePath")
    ifNull <- c.getOrElse[ValueOrRemove]("ifNull")(defaultIfNull)
    ifAbsent <- c.getOrElse[ValueOrRemove]("ifAbsent")(defaultIfAbsent)
  } yield SetWithDefaultEntry(sourcePath = sourcePath, ifNull = ifNull, ifAbsent = ifAbsent)
}

sealed trait JsonValue { // for the sake of performance we use vertx Json for body manipulation
  def asString: Option[String] =
    this match {
      case StringJsonValue(value) => Some(value)
      case BooleanJsonValue(value) => Some(value.toString)
      case NumberJsonValue(value) => Some(value.toString)
      case _ => None
    }

  def asListOfStrings: Option[List[String]] =
    this match {
      case StringJsonValue(value)  => Some(List(value))
      case BooleanJsonValue(value) => Some(List(value.toString))
      case NumberJsonValue(value)  => Some(List(value.toString))
      case ArrayJsonValue(value)   => Some(value.getList.asScala.toList.collect { case element if element.isInstanceOf[String] => element.asInstanceOf[String] })
      case _ => None
    }

  def rawValue: Any =
    this match {
      case NullJsonValue => null
      case StringJsonValue(value) => value
      case ObjectJsonValue(value) => value
      case ArrayJsonValue(value) => value
      case BooleanJsonValue(value) => value
      case NumberJsonValue(value) => value
    }
}
object JsonValue {
  def apply(json: Json): JsonValue = {
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

case object NullJsonValue extends JsonValue
case class StringJsonValue(value: String) extends JsonValue
case class ObjectJsonValue(value: JsonObject) extends JsonValue
case class ArrayJsonValue(value: JsonArray) extends JsonValue
case class BooleanJsonValue(value: Boolean) extends JsonValue
case class NumberJsonValue(value: Number) extends JsonValue

case class JsonValueIgnoreNullIfDefault(jsonValue: Option[JsonValue], ignoreNullIfAbsent: Boolean = false)
