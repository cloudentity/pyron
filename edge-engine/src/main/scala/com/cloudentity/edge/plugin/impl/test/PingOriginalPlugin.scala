package com.cloudentity.edge.plugin.impl.test

import com.cloudentity.edge.domain.flow.{PluginName, RequestCtx}
import com.cloudentity.edge.domain.http.ApiResponse
import com.cloudentity.edge.plugin.RequestPluginService
import com.cloudentity.edge.plugin.config.{ValidateOk, ValidateResponse}
import com.cloudentity.edge.plugin.verticle.RequestPluginVerticle
import com.cloudentity.tools.vertx.http.Headers
import io.circe.Decoder
import io.circe.syntax._
import io.vertx.core.buffer.Buffer
import com.cloudentity.edge.domain.Codecs._
import scala.concurrent.Future

class PingOriginalPlugin extends RequestPluginVerticle[Unit] with RequestPluginService {
  override def name: PluginName = PluginName("pingOriginal")

  override def apply(ctx: RequestCtx, conf: Unit): Future[RequestCtx] = {
    Future.successful(ctx.abort(ApiResponse(200, Buffer.buffer(ctx.original.asJson.noSpaces), Headers.of("Content-Type" -> "application/json"))))
  }

  override def validate(conf: Unit): ValidateResponse = ValidateOk

  override def confDecoder: Decoder[Unit] = Decoder.decodeUnit
}