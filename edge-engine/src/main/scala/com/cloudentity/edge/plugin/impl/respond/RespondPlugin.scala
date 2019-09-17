package com.cloudentity.edge.plugin.impl.respond

import com.cloudentity.edge.domain.Codecs
import io.circe.Decoder
import com.cloudentity.edge.domain.flow.{PluginName, RequestCtx}
import com.cloudentity.edge.plugin.config._
import com.cloudentity.edge.domain.http.ApiResponse
import com.cloudentity.edge.plugin.RequestPluginService
import com.cloudentity.edge.plugin.verticle.RequestPluginVerticle

import scala.concurrent.Future

class RespondPlugin extends RequestPluginVerticle[ApiResponse] with RequestPluginService {
  override def name: PluginName = PluginName("respond")

  override def apply(req: RequestCtx, conf: ApiResponse): Future[RequestCtx] =
    Future.successful(req.abort(conf))

  override def validate(conf: ApiResponse): ValidateResponse = ValidateOk

  override def confDecoder: Decoder[ApiResponse] = Codecs.apiResponseDec
}
