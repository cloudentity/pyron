package com.cloudentity.edge.sample.scala

import com.cloudentity.edge.domain.flow.{PluginName, RequestCtx}
import com.cloudentity.edge.domain.http.ApiResponse
import com.cloudentity.edge.plugin.config.{ValidateFailure, ValidateOk, ValidateResponse}
import com.cloudentity.edge.plugin.verticle.RequestPluginVerticle
import com.cloudentity.edge.util.ConfigDecoder
import com.cloudentity.tools.vertx.http.Headers
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.vertx.core.buffer.Buffer

import scala.concurrent.Future

case class RateLimitConf(perSecond: Int, counter: String)

class RateLimitPluginVerticle extends RequestPluginVerticle[RateLimitConf] with ConfigDecoder {
  override def name: PluginName = PluginName("sample-rate-limit")

  var counters: Map[String, Int] = Map().withDefaultValue(0)

  override def initService() = {
    vertx.setPeriodic(1000, _ => counters = Map().withDefaultValue(0))
  }

  override def apply(requestCtx: RequestCtx, conf: RateLimitConf): Future[RequestCtx] =
    Future.successful {
      if (counters(conf.counter) >= conf.perSecond) {
        requestCtx.abort(ApiResponse(429, Buffer.buffer(), Headers()))
      } else {
        counters = counters.updated(conf.counter, counters(conf.counter) + 1)
        requestCtx
      }
    }

  override def validate(conf: RateLimitConf): ValidateResponse =
    if (conf.counter.nonEmpty && conf.perSecond >= 0) {
      ValidateOk
    } else {
      ValidateFailure("'counter' must not be empty and 'perSecond' must be non-negative")
    }

  override def confDecoder: Decoder[RateLimitConf] = deriveDecoder
}
