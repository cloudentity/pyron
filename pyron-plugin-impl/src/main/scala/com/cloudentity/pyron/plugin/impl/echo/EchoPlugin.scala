package com.cloudentity.pyron.plugin.impl.echo

import com.cloudentity.pyron.domain.flow.{PluginName, RequestCtx}
import com.cloudentity.pyron.domain.http.{ApiResponse, Headers}
import com.cloudentity.pyron.plugin.config.{ValidateOk, ValidateResponse}
import com.cloudentity.pyron.plugin.verticle.RequestPluginVerticle
import com.cloudentity.pyron.util.ConfigDecoder
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.vertx.core.json.JsonObject

import scala.collection.JavaConverters._
import scala.concurrent.Future

case class EchoPluginConf( headers: Option[Boolean], body: Option[Boolean],
                           uri: Option[Boolean], method: Option[Boolean],
                           queryParams: Option[Boolean],
                           host: Option[Boolean],
                           headersList: Option[List[String]])

object EchoPluginConf {
  val default = EchoSettings(headers = Some(true), body = Some(false), uri = Some(false),
    method = Some(false), queryParams = Some(false), host = Some(false), headersList = Some(List()))
}

case class EchoSettings(headers: Option[Boolean], body: Option[Boolean], uri: Option[Boolean], method: Option[Boolean],
                        queryParams: Option[Boolean], host: Option[Boolean], headersList: Option[List[String]])

class EchoPlugin extends RequestPluginVerticle[EchoPluginConf] with ConfigDecoder {
  override def name: PluginName = PluginName("echo")
  override def apply(requestCtx: RequestCtx, conf: EchoPluginConf): Future[RequestCtx] = {
    val j =  new JsonObject()
      .put("headers", headersAsJsonObject(conf, requestCtx.targetRequest.headers))
      .put("queryParams", queryParamsResponse(conf, requestCtx))
      .put("uri", uriResponse(conf, requestCtx))
      .put("method", methodResponse(conf, requestCtx))
      .put("body", bodyResponse(conf, requestCtx))
    log.debug(requestCtx.tracingCtx, s"Response: $j")
    Future.successful(requestCtx.abort(ApiResponse(200, j.toBuffer, Headers.empty().set("content-type", "application/json"))))
  }

  def headersAsJsonObject(conf: EchoPluginConf, httpHeaders: Headers): JsonObject = {
    if(conf.headers.getOrElse(true)) {
      httpHeaders.toMap.foldLeft(new JsonObject())((json,mapElem) => json.put(mapElem._1, mapElem._2.asJava))
    } else {
      httpHeaders.toMap.foldLeft(new JsonObject())((json,mapElem) => {
        if(conf.headersList.contains(mapElem._1)) {
          json.put(mapElem._1, mapElem._2.asJava)
        } else json
      })
    }
  }

  def bodyResponse(conf: EchoPluginConf, requestCtx: RequestCtx): JsonObject = {
    if(conf.body.getOrElse(false)) {
      new JsonObject(requestCtx.targetRequest.bodyOpt.get)
    } else new JsonObject()
  }

  def uriResponse(conf: EchoPluginConf, requestCtx: RequestCtx): String = {
    if(conf.uri.getOrElse(false)) {
      requestCtx.targetRequest.uri.path
    } else ""
  }

  def methodResponse(conf: EchoPluginConf, requestCtx: RequestCtx): String = {
    if(conf.method.getOrElse(false)) {
      requestCtx.targetRequest.method.toString
    } else ""
  }

  def queryParamsResponse(conf: EchoPluginConf, requestCtx: RequestCtx): String = {
    if(conf.queryParams.getOrElse(false)) {
      requestCtx.targetRequest.uri.query.toString
    } else ""
  }

  def headerAsJson(requestCtx: RequestCtx) = {
    requestCtx.targetRequest.headers.toMap
  }

  override def validate(conf: EchoPluginConf): ValidateResponse = ValidateOk

  override def confDecoder: Decoder[EchoPluginConf] = deriveDecoder
}
