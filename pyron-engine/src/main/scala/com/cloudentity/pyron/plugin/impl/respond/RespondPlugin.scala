package com.cloudentity.pyron.plugin.impl.respond

import com.cloudentity.pyron.domain.Codecs
import io.circe.Decoder
import com.cloudentity.pyron.domain.flow.{PluginName, RequestCtx}
import com.cloudentity.pyron.plugin.config._
import com.cloudentity.pyron.domain.http.ApiResponse
import com.cloudentity.pyron.plugin.RequestPluginService
import com.cloudentity.pyron.plugin.verticle.RequestPluginVerticle

import scala.concurrent.Future

class RespondPlugin extends RequestPluginVerticle[ApiResponse] with RequestPluginService {
  override def name: PluginName = PluginName("respond")

  override def apply(req: RequestCtx, conf: ApiResponse): Future[RequestCtx] =
    Future.successful(req.abort(conf))

  override def validate(conf: ApiResponse): ValidateResponse = ValidateOk

  override def confDecoder: Decoder[ApiResponse] = Codecs.apiResponseDec
}
