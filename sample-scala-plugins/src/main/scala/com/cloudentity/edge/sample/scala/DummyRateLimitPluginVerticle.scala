package com.cloudentity.edge.sample.scala

import com.cloudentity.edge.domain.flow.{PluginName, RequestCtx, ResponseCtx}
import com.cloudentity.edge.domain.http.ApiResponse
import com.cloudentity.edge.plugin.config.{ValidateFailure, ValidateOk, ValidateResponse}
import com.cloudentity.edge.plugin.verticle.RequestResponsePluginVerticle
import com.cloudentity.edge.util.ConfigDecoder
import com.cloudentity.tools.vertx.http.Headers
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.vertx.core.buffer.Buffer

import scala.concurrent.Future

case class RateLimitConf(maxConcurrent: Int, counter: String)

class DummyRateLimitPluginVerticle extends RequestResponsePluginVerticle[RateLimitConf] with ConfigDecoder {
  override def name: PluginName = PluginName("sample-rate-limit")

  var counters: Map[String, Int] = Map().withDefaultValue(0)

  private val shouldDecreaseCounterKey = s"${name.value}.should-decrease-counter"

  override def apply(requestCtx: RequestCtx, conf: RateLimitConf): Future[RequestCtx] =
    Future.successful {
      if (counters(conf.counter) >= conf.maxConcurrent) {
        requestCtx.abort(ApiResponse(429, Buffer.buffer(), Headers()))
      } else {
        counters = counters.updated(conf.counter, counters(conf.counter) + 1)

        requestCtx.modifyProperties(_.updated(shouldDecreaseCounterKey, true))
      }
    }

  override def apply(responseCtx: ResponseCtx, conf: RateLimitConf): Future[ResponseCtx] =
    Future.successful {
      val shouldDecreaseCounter = responseCtx.properties.get(shouldDecreaseCounterKey).getOrElse(false)
      if (shouldDecreaseCounter) {
        counters = counters.updated(conf.counter, counters(conf.counter) - 1)
      }

      responseCtx
    }

  override def validate(conf: RateLimitConf): ValidateResponse =
    if (conf.counter.nonEmpty && conf.maxConcurrent >= 0) {
      ValidateOk
    } else {
      ValidateFailure("'counter' must not be empty and 'maxConcurrent' must be non-negative")
    }

  override def confDecoder: Decoder[RateLimitConf] = deriveDecoder
}
