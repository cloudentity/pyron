package com.cloudentity.pyron.plugin.impl.test

import com.cloudentity.pyron.domain.Codecs
import io.circe.Decoder
import com.cloudentity.pyron.plugin.config._
import com.cloudentity.pyron.domain.flow.{PluginName, RequestCtx}
import com.cloudentity.pyron.domain.http.ApiResponse
import com.cloudentity.pyron.plugin.RequestPluginService
import com.cloudentity.pyron.plugin.verticle.RequestPluginVerticle
import com.cloudentity.tools.vertx.http.Headers
import io.vertx.core.buffer.Buffer

import scala.concurrent.Future

class PingPlugin extends RequestPluginVerticle[Unit] with RequestPluginService {
  override def name: PluginName = PluginName("ping")

  override def apply(ctx: RequestCtx, conf: Unit): Future[RequestCtx] =
    Future.successful(ctx.abort(ApiResponse(200, Buffer.buffer(Codecs.requestCtxEnc(ctx).toString()), Headers.of("Content-Type" -> "application/json"))))

  override def validate(conf: Unit): ValidateResponse = ValidateOk

  override def confDecoder: Decoder[Unit] = Decoder.decodeUnit
}
