package com.cloudentity.pyron.plugin.impl.httpcall

import com.cloudentity.pyron.plugin.verticle.RequestPluginVerticle
import io.circe.Decoder
import com.cloudentity.pyron.domain.flow.PluginName
import com.cloudentity.pyron.domain.flow.RequestCtx
import scala.concurrent.Future
import com.cloudentity.pyron.plugin.config.ValidateResponse
import io.circe.generic.semiauto._
import com.cloudentity.tools.vertx.http.SmartHttpClient
import com.cloudentity.tools.vertx.http.client.SmartHttpClientImpl
import com.cloudentity.tools.vertx.http.builder.SmartHttpClientBuilder
import com.cloudentity.tools.vertx.http.builder.SmartHttpClientBuilderImpl
import com.cloudentity.tools.vertx.http.SmartHttp
import io.vertx.core.http.HttpMethod
import com.cloudentity.pyron.plugin.bus.response
import com.cloudentity.pyron.domain.Codecs._


case class HttpCallConf(uri: String, method: HttpMethod, propertiesKey: String)

class HttpCallPlugin extends RequestPluginVerticle[HttpCallConf] {
var client: SmartHttpClient = _

  override def initServiceAsyncS(): Future[Unit] = 
    SmartHttp.clientBuilder(vertx, getConfig().getJsonObject("client")).build().toScala().map(client = _)

  override def confDecoder: Decoder[HttpCallConf] = deriveDecoder

  override def name: PluginName = PluginName("http-call")

  override def apply(requestCtx: RequestCtx, conf: HttpCallConf): Future[RequestCtx] = {
    client.request(conf.method, conf.uri).endWithBody().toScala.map { response =>
      val jsonBody = response.getBody().toJsonObject()
      requestCtx.modifyProperties(_.updated(conf.propertiesKey, jsonBody))
    }
  }

  override def validate(conf: HttpCallConf): ValidateResponse = ValidateResponse.ok()


}
