package com.cloudentity.pyron.plugin.impl.test

import com.cloudentity.pyron.domain.Codecs._
import io.circe.Decoder
import com.cloudentity.pyron.plugin.config._
import com.cloudentity.pyron.domain.flow.{FlowFailure, PluginName, RequestCtx}
import com.cloudentity.pyron.domain.http.ApiResponse
import com.cloudentity.pyron.plugin.RequestPluginService
import com.cloudentity.pyron.plugin.verticle.RequestPluginVerticle
import com.cloudentity.tools.vertx.http.Headers
import io.vertx.core.buffer.Buffer

import scala.concurrent.Future
import io.circe._
import io.circe.generic.semiauto._
import io.vertx.core.streams.Pipe

class PingPlugin extends RequestPluginVerticle[Unit] with RequestPluginService {
  override def name: PluginName = PluginName("ping")

    implicit lazy val failureEnc: Encoder[FlowFailure] =
      Encoder.encodeString.contramap(_.toString)

  import io.circe.generic.auto._
  implicit lazy val pipeBufferEnc: Encoder[Pipe[Buffer]] = Encoder.encodeString.contramap(_ => "")

  implicit lazy val requestCtxEnc: Encoder[RequestCtx] = deriveEncoder

  override def apply(ctx: RequestCtx, conf: Unit): Future[RequestCtx] = {
    Future.successful(ctx.abort(ApiResponse(200, Buffer.buffer(requestCtxEnc(ctx).toString()), Headers.of("Content-Type" -> "application/json"))))
  }

  override def validate(conf: Unit): ValidateResponse = ValidateOk

  override def confDecoder: Decoder[Unit] = Decoder.decodeUnit
}
