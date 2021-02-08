package com.cloudentity.pyron.plugin.util.value

import io.circe.{Decoder, Json, KeyDecoder}
import io.vertx.core.json.{JsonArray, JsonObject}
import scala.collection.JavaConverters._

sealed trait ValueOrRef
case class Value(value: Json) extends ValueOrRef
sealed trait RefType extends ValueOrRef
case class BodyRef(path: Path) extends RefType
case class PathParamRef(param: String) extends RefType
case class QueryParamRef(param: String) extends RefType
case class AuthnRef(path: Path) extends RefType
case class HeaderRef(header: String, typ: HeaderRefType) extends RefType

case object HostRef extends RefType
case object HostNameRef extends RefType

case object HostPortRef extends RefType
case object SchemeRef extends RefType

case object LocalHostRef extends RefType
case object RemoteHostRef extends RefType
case class CookieRef(cookie: String) extends RefType

sealed trait HeaderRefType extends RefType
case object FirstHeaderRefType extends HeaderRefType
case object AllHeaderRefType extends HeaderRefType

object ValueOrRef {

  implicit val ValueOrRefDecoder: Decoder[ValueOrRef] = Decoder.decodeJson.emap { json =>
    json.asString.filter(_.startsWith("$")).map {
      decodeReference
    } getOrElse Right(Value(json))
  }

  private def decodeReference(string: String): Either[String, RefType] = {
    // FIX: check corner cases when splitting
    string.substring(1).split('.').toList match {
      case refType :: path => refType match {
        case "cookies" => decodeCookieRef(path)
        case "host" => Right(HostRef)
        case "hostName" if path.isEmpty => Right(HostNameRef)
        case "hostPort" => Right(HostPortRef)
        case "scheme" => Right(SchemeRef)
        case "localHost" => Right(LocalHostRef)
        case "remoteHost" => Right(RemoteHostRef)
        case "body" => Right(BodyRef(Path(path)))
        case "authn" => Right(AuthnRef(Path(path)))
        case "pathParams" => Right(PathParamRef(path.mkString(".")))
        case "queryParams" => decodeQueryParamRef(path)
        case "headers" => decodeHeadersRef(path)
        case x => Left(s"invalid reference type: $x")
      }
      case Nil => Left("reference cannot be empty")
    }
  }

  private def decodeQueryParamRef(path: List[String]): Either[String, QueryParamRef] = path match {
    case queryParamName :: Nil => Right(QueryParamRef(queryParamName))
    case _ => Left(s"invalid query param name: $path")
  }
  private def decodeCookieRef(path: List[String]): Either[String, CookieRef] = path match {
    case cookieName :: Nil => Right(CookieRef(cookieName))
    case _ => Left(s"invalid cookie name: $path")
  }

  private def decodeHeadersRef(path: List[String]): Either[Nothing, HeaderRef] = Right {
    path match {
      case header :: "*" :: Nil =>
        HeaderRef(header, AllHeaderRefType)
      case path =>
        HeaderRef(path.mkString("."), FirstHeaderRefType)
    }
  }
}

case class Path(value: List[String])
object Path {
  implicit val PathDecoder: KeyDecoder[Path] = key => Some(Path(key.split('.').toList))

  def apply(xs: String*): Path = Path(xs.toList)
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

case object NullJsonValue extends JsonValue
case class StringJsonValue(value: String) extends JsonValue
case class ObjectJsonValue(value: JsonObject) extends JsonValue
case class ArrayJsonValue(value: JsonArray) extends JsonValue
case class BooleanJsonValue(value: Boolean) extends JsonValue
case class NumberJsonValue(value: Number) extends JsonValue
