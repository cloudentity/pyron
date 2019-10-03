package com.cloudentity.pyron.plugin.impl.test

import com.cloudentity.pyron.domain.flow.{PluginName, RequestCtx}
import com.cloudentity.pyron.domain.http.ApiResponse
import com.cloudentity.pyron.plugin.RequestPluginService
import com.cloudentity.pyron.plugin.config.{ValidateOk, ValidateResponse}
import com.cloudentity.pyron.plugin.verticle.RequestPluginVerticle
import com.cloudentity.tools.vertx.http.Headers
import io.circe.Decoder
import io.circe.syntax._
import io.vertx.core.buffer.Buffer
import com.cloudentity.pyron.domain.Codecs._
import scala.concurrent.Future

class PingOriginalPlugin extends RequestPluginVerticle[Unit] with RequestPluginService {
  override def name: PluginName = PluginName("pingOriginal")

  override def apply(ctx: RequestCtx, conf: Unit): Future[RequestCtx] = {
    Future.successful(ctx.abort(ApiResponse(200, Buffer.buffer(ctx.original.asJson.noSpaces), Headers.of("Content-Type" -> "application/json"))))
  }

  override def validate(conf: Unit): ValidateResponse = ValidateOk

  override def confDecoder: Decoder[Unit] = Decoder.decodeUnit
}