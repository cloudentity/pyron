package com.cloudentity.edge.plugin.impl.authn

import io.circe.Decoder
import com.cloudentity.edge.plugin.config._
import com.cloudentity.edge.domain.flow._
import com.cloudentity.edge.plugin.verticle.RequestPluginVerticle
import io.vertx.core.buffer.Buffer
import io.circe.parser._
import io.circe.syntax._
import com.cloudentity.edge.plugin.bus.request._

import scala.concurrent.Future
import codecs._
import com.cloudentity.edge.domain.flow.{PluginConf, PluginName}
import com.cloudentity.edge.domain.http.ApiResponse
import com.cloudentity.edge.plugin.RequestPluginService
import com.cloudentity.tools.vertx.http.Headers

class AuthnProxyPlugin extends RequestPluginVerticle[Unit] with RequestPluginService {
  override def name: PluginName = PluginName("authn-proxy")

  var authnPluginClient: RequestPluginService = _

  override def initService(): Unit = {
    authnPluginClient = createClient(classOf[RequestPluginService], "authn")
  }

  override def apply(ctx: RequestCtx, conf: Unit): Future[RequestCtx] = {
    val body = ctx.request.bodyOpt.getOrElse(Buffer.buffer())

    decode[AuthnProxyPluginRequest](body.toString) match {
      case Right(authnRequest) =>
        val authnPluginConf = authnRequest.conf.asJson
        val applyRequest = ApplyRequest(
          // we assume authentication is based only on headers that we got from edge-gateway
          ctx = ctx.modifyRequest(_.copy(headers = Headers(authnRequest.request.headers))),
          conf = PluginConf(PluginName("authn"), authnPluginConf)
        )
        log.debug(ctx.tracingCtx, s"Executing AuthnPlugin with conf=${authnPluginConf.noSpaces}")
        authnPluginClient.applyPlugin(ctx.tracingCtx, applyRequest).toScala
            .flatMap {
              case Continue(resultCtx) =>
                val response =
                  resultCtx.aborted match {
                    case Some(response) =>
                      response
                    case None =>
                      val flowCtx = resultCtx.authnCtx.value.map((FlowCtx.apply _).tupled).toList
                      val responseBody = AuthnProxyPluginResponse(flowCtx).asJson.noSpaces
                      ApiResponse(200, Buffer.buffer(responseBody), Headers.of("Content-Type" -> "application/json"))
                  }
                Future.successful(ctx.abort(response))
              case ApplyError(msg) =>
                Future.failed(new Exception(msg))
            }
      case Left(error) =>
        Future.failed(error.getCause)
    }
  }

  override def validate(conf: Unit): ValidateResponse = ValidateOk

  override def confDecoder: Decoder[Unit] = Decoder.decodeUnit
}
