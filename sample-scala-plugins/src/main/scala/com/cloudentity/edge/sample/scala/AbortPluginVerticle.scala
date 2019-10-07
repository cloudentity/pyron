package com.cloudentity.pyron.sample.scala

import com.cloudentity.pyron.domain.flow.{PluginName, RequestCtx}
import com.cloudentity.pyron.domain.http.ApiResponse
import com.cloudentity.pyron.plugin.config.{ValidateFailure, ValidateOk, ValidateResponse}
import com.cloudentity.pyron.plugin.verticle.RequestPluginVerticle
import com.cloudentity.pyron.util.ConfigDecoder
import com.cloudentity.tools.vertx.http.{Headers, SmartHttp, SmartHttpClient}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject

import scala.concurrent.Future

case class AbortConf(header: String)
case class AbortPluginVerticleConf(path: String, client: JsonObject)

class AbortPluginVerticle extends RequestPluginVerticle[AbortConf] with ConfigDecoder {
  override def name: PluginName = PluginName("sample-abort")

  var verticleConf: AbortPluginVerticleConf = _
  var client: SmartHttpClient = _

  override def initServiceAsyncS(): Future[Unit] = {
    implicit val PluginConfDecoder = deriveDecoder[AbortPluginVerticleConf]

    verticleConf = decodeConfigUnsafe[AbortPluginVerticleConf]
    SmartHttp.clientBuilder(vertx, verticleConf.client)
      .build().toScala.map(c => client = c)
  }

  override def apply(requestCtx: RequestCtx, conf: AbortConf): Future[RequestCtx] = {
    requestCtx.request.headers.get(conf.header) match {
      case Some(value) =>
        client.get(verticleConf.path)
          .putHeader(conf.header, value)
          .end().toScala()
          .map { response =>
            if (response.statusCode() == 200) {
              requestCtx
            } else {
              requestCtx.abort(ApiResponse(403, Buffer.buffer(), Headers()))
            }
          }
      case None =>
        Future.successful(
          requestCtx.abort(ApiResponse(403, Buffer.buffer(), Headers()))
        )
    }
  }

  override def validate(conf: AbortConf): ValidateResponse =
    if (conf.header.nonEmpty) {
      ValidateOk
    } else {
      ValidateFailure("'header' must not be empty")
    }

  override def confDecoder: Decoder[AbortConf] = deriveDecoder
}
