package com.cloudentity.pyron.plugin.impl.transformer

import io.circe.{Decoder, Json, KeyDecoder}
import io.circe.generic.semiauto.deriveDecoder
import io.vertx.core.json.{JsonArray, JsonObject}
import scala.collection.JavaConverters._

// root conf
case class TransformerConf(body: BodyOps, parseJsonBody: Boolean, pathParams: PathParamOps, headers: HeaderOps)
case class TransformerConfRaw(body: Option[BodyOps], pathParams: Option[PathParamOps], headers: Option[HeaderOps]) // actual JSON schema

// transformations
sealed trait TransformOps

case class BodyOps(set: Option[Map[Path, ValueOrRef]], drop: Option[Boolean]) extends TransformOps
case class ResolvedBodyOps(set: Option[Map[Path, Option[JsonValue]]], drop: Option[Boolean])

case class PathParamOps(set: Option[Map[String, ValueOrRef]]) extends TransformOps
case class ResolvedPathParamOps(set: Option[Map[String, Option[String]]])

case class HeaderOps(set: Option[Map[String, ValueOrRef]]) extends TransformOps
case class ResolvedHeaderOps(set: Option[Map[String, Option[List[String]]]])

// references
sealed trait ValueOrRef
  case class Value(value: Json) extends ValueOrRef
  case class BodyRef(path: Path) extends ValueOrRef
  case class PathParamRef(param: String) extends ValueOrRef
  case class AuthnRef(path: Path) extends ValueOrRef
  case class HeaderRef(header: String, typ: HeaderRefType) extends ValueOrRef

sealed trait HeaderRefType
  case object FirstHeaderRefType extends HeaderRefType
  case object AllHeaderRefType extends HeaderRefType

case class Path(value: List[String])
object Path {
  def apply(xs: String*): Path = Path(xs.toList)
}

object TransformerConf {
  implicit val PathDecoder: KeyDecoder[Path] = key => Some(Path(key.split("\\.").toList))
  implicit val ValueOrRefDecoder: Decoder[ValueOrRef] = Decoder.decodeJson.emap { json =>
    json.asString match {
      case Some(string) =>
        if (string.startsWith("$"))
          string.substring(1).split("\\.").toList match {
            case refType :: path =>
              val ref =
                refType match {
                  case "body" =>
                    BodyRef(Path(path))
                  case "authn" =>
                    AuthnRef(Path(path))
                  case "pathParams" =>
                    PathParamRef(path.mkString("."))
                  case "headers" =>
                    path match {
                      case header :: "*" :: Nil =>
                        HeaderRef(header, AllHeaderRefType)
                      case path =>
                        HeaderRef(path.mkString("."), FirstHeaderRefType)
                    }
                }

              Right(ref)

            case Nil =>
              Left("reference cannot be empty")
          }
        else Right(Value(json))
      case None => Right(Value(json))
    }
  }

  implicit val BodyOpsDecoder: Decoder[BodyOps] = deriveDecoder[BodyOps].emap { ops =>
    if (ops.set.isDefined && ops.drop.getOrElse(false)) Left("Can't both drop body and set body attribute")
    else                                                Right(ops)
  }
  implicit val PathParamOpsDecoder: Decoder[PathParamOps] = deriveDecoder
  implicit val HeaderOpsDecoder: Decoder[HeaderOps] = deriveDecoder

  val TransformerConfRawDecoder: Decoder[TransformerConfRaw] = deriveDecoder
  implicit val TransformerConfDecoder: Decoder[TransformerConf] = TransformerConfRawDecoder.map { rawConf =>
    TransformerConf(
      body          = rawConf.body.getOrElse(BodyOps(None, None)),
      parseJsonBody = jsonBodyReferenceExists(rawConf) || rawConf.body.isDefined,
      pathParams    = rawConf.pathParams.getOrElse(PathParamOps(None)),
      headers       = rawConf.headers.getOrElse(HeaderOps(None))
    )
  }

  private def jsonBodyReferenceExists(conf: TransformerConfRaw): Boolean =
    List[Option[TransformOps]](conf.body, conf.pathParams, conf.headers).flatten.find {
      case BodyOps(set, _)   => jsonBodyReferenceExists(set)
      case PathParamOps(set) => jsonBodyReferenceExists(set)
      case HeaderOps(set)    => jsonBodyReferenceExists(set)
    }.isDefined

  private def jsonBodyReferenceExists(refs: Option[Map[_, ValueOrRef]]): Boolean =
    refs.map(_.values).toList.flatten.exists(_.isInstanceOf[BodyRef])
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