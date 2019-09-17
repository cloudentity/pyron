package com.cloudentity.edge.plugin.impl.headers

import io.circe.{Decoder, Json}
import com.cloudentity.edge.plugin.config._
import com.cloudentity.edge.plugin.RequestPluginService
import com.cloudentity.edge.plugin.verticle.RequestPluginVerticle
import io.circe.generic.semiauto._
import com.cloudentity.edge.domain.flow.{PluginName, RequestCtx}

import scala.concurrent.Future

case class HeaderToFlowCtxConf(headers: List[String])

/**
  * Copies header values from request to flow-ctx.
  */
class HeaderToFlowCtxPlugin extends RequestPluginVerticle[HeaderToFlowCtxConf] with RequestPluginService {
  override def name: PluginName = PluginName("headerToCtx")

  override def validate(conf: HeaderToFlowCtxConf): ValidateResponse = ValidateOk
  override def confDecoder: Decoder[HeaderToFlowCtxConf] = deriveDecoder[HeaderToFlowCtxConf]

  override def apply(requestCtx: RequestCtx, conf: HeaderToFlowCtxConf): Future[RequestCtx] =
    Future.successful {
      val ctx: Map[String, Json] =
        conf.headers.flatMap { headerName =>
          requestCtx.request.headers.getValues(headerName).flatMap {
            case value :: Nil => Some((headerName, Json.fromString(value))) // if single value then put single string
            case Nil          => None                                              // if no values then skip
            case values       => Some((headerName, Json.arr(values.map(Json.fromString):_*))) // if multiple values then put string array
          }
        }.toMap

      requestCtx.modifyAuthnCtx(_.mergeMap(ctx))
    }

}
