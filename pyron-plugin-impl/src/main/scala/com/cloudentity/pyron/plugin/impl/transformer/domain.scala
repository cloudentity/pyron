package com.cloudentity.pyron.plugin.impl.transformer

import com.cloudentity.pyron.plugin.util.value._
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

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

object TransformerConf {
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
    List[Option[TransformOps]](conf.body, conf.pathParams, conf.headers).flatten.exists {
      case BodyOps(set, _) => jsonBodyReferenceExists(set)
      case PathParamOps(set) => jsonBodyReferenceExists(set)
      case HeaderOps(set) => jsonBodyReferenceExists(set)
    }

  private def jsonBodyReferenceExists(refs: Option[Map[_, ValueOrRef]]): Boolean =
    refs.map(_.values).toList.flatten.exists(_.isInstanceOf[BodyRef])
}
