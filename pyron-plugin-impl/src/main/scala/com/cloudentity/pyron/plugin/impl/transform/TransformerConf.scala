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
case class RequestTransformerConfRaw(body: Option[BodyOps],
                              pathParams: Option[PathParamOps],
                              queryParams: Option[QueryParamOps],
                              headers: Option[HeaderOps])
case class ResponseTransformerConfRaw(body: Option[BodyOps],
                              headers: Option[HeaderOps],
                              status: Option[Int])
// root conf
case class RequestTransformerConf(body: BodyOps,
                                   parseRequestJsonBody: Boolean,
                                   pathParams: PathParamOps,
                                   queryParams: QueryParamOps,
                                   headers: HeaderOps)
case class ResponseTransformerConf(body: BodyOps,
                           parseRequestJsonBody: Boolean,
                           parseResponseJsonBody: Boolean,
                           headers: HeaderOps,
                           status: Option[Int])

object Ops {
  implicit val BodyOpsDecoder: Decoder[BodyOps] = deriveDecoder[BodyOps].emap {
    case BodyOps(Some(_), _, Some(true), _) => Left("Can't both drop body and set body attribute")
    case ops => Right(ops)
  }
  implicit val PathParamOpsDecoder: Decoder[PathParamOps] = deriveDecoder
  implicit val QueryParamOpsDecoder: Decoder[QueryParamOps] = deriveDecoder
  implicit val HeaderOpsDecoder: Decoder[HeaderOps] = deriveDecoder
}

object RequestTransformerConf {
  import Ops._

  implicit val TransformerConfDecoder: Decoder[RequestTransformerConf] = deriveDecoder[RequestTransformerConfRaw].map {
    rawConf =>
      RequestTransformerConf(
        body = rawConf.body.getOrElse(BodyOps(None, None, None, None)),
        parseRequestJsonBody = rawConf.body.nonEmpty || jsonRequestBodyRefExists(rawConf.body, rawConf.pathParams, rawConf.headers),
        pathParams = rawConf.pathParams.getOrElse(PathParamOps(None)),
        queryParams = rawConf.queryParams.getOrElse(QueryParamOps(None)),
        headers = rawConf.headers.getOrElse(HeaderOps(None))
      )
  }

  def jsonRequestBodyRefExists(body: Option[BodyOps], pathParams: Option[PathParamOps], headers: Option[HeaderOps]): Boolean = {
    Seq(
      body.flatMap(_.set),
      pathParams.flatMap(_.set),
      headers.flatMap(_.set)
    ).flatten.exists(_.values.exists(_.isInstanceOf[RequestBodyRef]))
  }
}

object ResponseTransformerConf {
  import Ops._

  implicit val TransformerConfDecoder: Decoder[ResponseTransformerConf] = deriveDecoder[ResponseTransformerConfRaw].map {
    rawConf =>
      ResponseTransformerConf(
        body = rawConf.body.getOrElse(BodyOps(None, None, None, None)),
        parseRequestJsonBody = RequestTransformerConf.jsonRequestBodyRefExists(rawConf.body, None, rawConf.headers),
        parseResponseJsonBody = rawConf.body.nonEmpty || jsonResponseBodyRefExists(rawConf),
        headers = rawConf.headers.getOrElse(HeaderOps(None)),
        status = rawConf.status)
  }

  private def jsonResponseBodyRefExists(conf: ResponseTransformerConfRaw): Boolean = {
    Seq(
      conf.body.flatMap(_.set),
      conf.headers.flatMap(_.set)
    ).flatten.exists(_.values.exists(_.isInstanceOf[ResponseBodyRef]))
  }

}
