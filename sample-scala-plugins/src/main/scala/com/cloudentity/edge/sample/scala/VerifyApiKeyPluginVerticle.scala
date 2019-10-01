package com.cloudentity.edge.sample.scala

import com.cloudentity.edge.domain.flow.{PluginName, RequestCtx}
import com.cloudentity.edge.domain.http.ApiResponse
import com.cloudentity.edge.plugin.config.{ValidateFailure, ValidateOk, ValidateResponse}
import com.cloudentity.edge.plugin.verticle.RequestPluginVerticle
import com.cloudentity.edge.util.ConfigDecoder
import com.cloudentity.tools.vertx.http.Headers
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, Json}
import io.vertx.core.buffer.Buffer

import scala.concurrent.Future

case class VerifyApiKeyConf(apiKey: String)
case class VerifyApiKeyVerticleConf(invalidKeyStatusCode: Int, invalidKeyBody: Option[Json], defaultApiKeyHeader: String)

class VerifyApiKeyPluginVerticle extends RequestPluginVerticle[VerifyApiKeyConf] with ConfigDecoder {
  override def name: PluginName = PluginName("verify-apikey")

  var verticleConf: VerifyApiKeyVerticleConf = _
  var unauthorizedResponse: ApiResponse = _

  override def initService(): Unit = {
    implicit val PluginConfDecoder = deriveDecoder[VerifyApiKeyVerticleConf]
    verticleConf = decodeConfigUnsafe[VerifyApiKeyVerticleConf]

    unauthorizedResponse =
      ApiResponse(
        statusCode = verticleConf.invalidKeyStatusCode,
        body       = verticleConf.invalidKeyBody.map(_.noSpaces).map(Buffer.buffer).getOrElse(Buffer.buffer()),
        headers    = Headers()
      )
  }

  override def apply(requestCtx: RequestCtx, conf: VerifyApiKeyConf): Future[RequestCtx] =
    Future.successful {
      val apiKeyValueOpt = requestCtx.request.headers.get(verticleConf.defaultApiKeyHeader)

      apiKeyValueOpt match {
        case Some(value) if (value == conf.apiKey) =>
          requestCtx
        case _ =>
          requestCtx.abort(unauthorizedResponse)
      }
    }

  override def validate(conf: VerifyApiKeyConf): ValidateResponse =
    if (conf.apiKey.nonEmpty) ValidateOk
    else                      ValidateFailure("'apiKey' must be not empty")

  override def confDecoder: Decoder[VerifyApiKeyConf] = deriveDecoder
}