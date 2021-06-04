package com.cloudentity.pyron.plugin.impl.acp

import com.cloudentity.pyron.apigroup.ApiGroup
import com.cloudentity.pyron.domain.flow.{PluginName, RequestCtx}
import com.cloudentity.pyron.domain.http.ApiResponse
import com.cloudentity.pyron.plugin.config.ValidateResponse
import com.cloudentity.pyron.plugin.verticle.RequestPluginVerticle
import com.cloudentity.tools.vertx.http.builder.SmartHttpResponse
import com.cloudentity.tools.vertx.http.{Headers, SmartHttp, SmartHttpClient}
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._
import io.vertx.core.buffer.Buffer

import scala.concurrent.Future

case class AuthorizeRequest(
  apiGroupId: String,
  method: String,
  path: String,
  headers: Map[String, List[String]],
  queryParams: Map[String, List[String]],
  pathParams: Map[String, String]
)

object AuthorizeRequest {
  implicit val AuthorizeRequestEncoder = new Encoder[AuthorizeRequest] {
    override def apply(a: AuthorizeRequest): Json =
      Json.obj(
        "method" -> a.method.asJson,
        "path" -> a.path.asJson,
        "api_group" -> a.apiGroupId.asJson,
        "headers" -> a.headers.asJson,
        "path_params" -> a.pathParams.asJson,
        "query_params" -> a.queryParams.asJson,
      )
  }
}

object AcpAuthzPlugin {
  val pluginName = PluginName("acp-authz")
}

class AcpAuthzPlugin extends RequestPluginVerticle[Unit] {
  override def name: PluginName = AcpAuthzPlugin.pluginName
  override def validate(conf: Unit): ValidateResponse = ValidateResponse.ok()
  override def confDecoder: Decoder[Unit] = Decoder.decodeUnit

  var client: SmartHttpClient = _

  val unauthorized = ApiResponse(403, Buffer.buffer(), Headers.empty())

  override def initServiceAsyncS(): Future[Unit] =
    SmartHttp.clientBuilder(vertx, getConfig.getJsonObject("authorizerClient"))
      .build().toScala().map(client = _)

  override def apply(requestCtx: RequestCtx, conf: Unit): Future[RequestCtx] =
    requestCtx.properties.get[ApiGroup](ApiGroup.propertiesKey) match {
      case Some(group) =>
        val originalReq = requestCtx.originalRequest
        val endpointPath = originalReq.path.value.drop(group.matchCriteria.basePath.map(_.value.length).getOrElse(0))

        val authzReq =
          AuthorizeRequest(
            group.id.value,
            originalReq.method.toString,
            endpointPath,
            originalReq.headers.toMap,
            originalReq.queryParams.toMap,
            originalReq.pathParams.value
          )

        client.post("/authorize").endWithBody(authzReq.asJson.noSpaces).toScala()
          .map(enforcementDecision(requestCtx))
      case None =>
        Future.failed(new Exception("apiGroup not found in request properties"))
    }

  private def enforcementDecision(requestCtx: RequestCtx)(response: SmartHttpResponse) = {
    val code = response.getHttp.statusCode

    if (code == 200) {
      log.debug(requestCtx.tracingCtx, "Request authorized, passing through")
      requestCtx
    } else if (code == 403) {
      log.debug(requestCtx.tracingCtx, s"Request unauthorized, aborting: code=$code, body=${response.getBody}")
      requestCtx.abort(unauthorized)
    } else {
      log.error(requestCtx.tracingCtx, s"Unexcepted acp-authorizer response: code=$code, body=${response.getBody}")
      requestCtx.abort(unauthorized)
    }
  }
}
