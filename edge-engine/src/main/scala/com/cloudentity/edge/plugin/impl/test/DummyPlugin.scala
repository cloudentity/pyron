package com.cloudentity.edge.plugin.impl.test

import io.circe.Decoder
import com.cloudentity.edge.domain.flow.{PluginName, RequestCtx}
import com.cloudentity.edge.plugin.config.{ValidateOk, ValidateResponse}
import com.cloudentity.edge.plugin.verticle.RequestPluginVerticle

import scala.concurrent.Future

class DummyPlugin extends RequestPluginVerticle[Unit] {
  override def name: PluginName = PluginName("dummy")

  override def apply(requestCtx: RequestCtx, conf: Unit): Future[RequestCtx] = Future.successful(requestCtx)

  override def validate(conf: Unit): ValidateResponse = ValidateOk

  override def confDecoder: Decoder[Unit] = Decoder.decodeUnit
}
