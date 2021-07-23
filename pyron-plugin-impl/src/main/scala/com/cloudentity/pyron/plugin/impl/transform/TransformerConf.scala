package com.cloudentity.pyron.plugin.impl.transform

import com.cloudentity.pyron.plugin.util.value._
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

// transformations
sealed trait TransformOps

case class BodyOps(set: Option[Map[Path, ValueOrRef]], remove: Option[List[Path]], drop: Option[Boolean], nullIfAbsent: Option[Boolean]) extends TransformOps
case class ResolvedBodyOps(set: Option[Map[Path, Option[JsonValue]]], remove: Option[List[Path]], drop: Option[Boolean], nullIfAbsent: Option[Boolean])

case class PathParamOps(set: Option[Map[String, ValueOrRef]]) extends TransformOps
case class ResolvedPathParamOps(set: Option[Map[String, Option[String]]])

case class QueryParamOps(set: Option[Map[String, ValueOrRef]]) extends TransformOps
case class ResolvedQueryParamOps(set: Option[Map[String, Option[List[String]]]])

case class HeaderOps(set: Option[Map[String, ValueOrRef]]) extends TransformOps
case class ResolvedHeaderOps(set: Option[Map[String, Option[List[String]]]])

// actual JSON schema
case class TransformerConfRaw(body: Option[BodyOps],
                              pathParams: Option[PathParamOps],
                              queryParams: Option[QueryParamOps],
                              headers: Option[HeaderOps])
// root conf
case class TransformerConf(body: BodyOps,
                           parseJsonBody: Boolean,
                           pathParams: PathParamOps,
                           queryParams: QueryParamOps,
                           headers: HeaderOps)

object TransformerConf {
  implicit val BodyOpsDecoder: Decoder[BodyOps] = deriveDecoder[BodyOps].emap {
    case BodyOps(Some(_), _, Some(true), _) => Left("Can't both drop body and set body attribute")
    case ops => Right(ops)
  }
  implicit val PathParamOpsDecoder: Decoder[PathParamOps] = deriveDecoder
  implicit val QueryParamOpsDecoder: Decoder[QueryParamOps] = deriveDecoder
  implicit val HeaderOpsDecoder: Decoder[HeaderOps] = deriveDecoder

  implicit val TransformerConfDecoder: Decoder[TransformerConf] = deriveDecoder[TransformerConfRaw].map {
    rawConf =>
      TransformerConf(
        body = rawConf.body.getOrElse(BodyOps(None, None, None, None)),
        parseJsonBody = rawConf.body.nonEmpty || jsonBodyRefExists(rawConf),
        pathParams = rawConf.pathParams.getOrElse(PathParamOps(None)),
        queryParams = rawConf.queryParams.getOrElse(QueryParamOps(None)),
        headers = rawConf.headers.getOrElse(HeaderOps(None))
      )
  }

  private def jsonBodyRefExists(conf: TransformerConfRaw): Boolean = {
    Seq(
      conf.body.flatMap(_.set),
      conf.pathParams.flatMap(_.set),
      conf.headers.flatMap(_.set)
    ).flatten.exists(_.values.exists(_.isInstanceOf[BodyRef]))
  }

}

