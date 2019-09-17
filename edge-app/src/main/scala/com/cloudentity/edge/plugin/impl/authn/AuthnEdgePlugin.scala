package com.cloudentity.edge.plugin.impl.authn

import java.util.concurrent.TimeUnit

import io.circe.Decoder
import com.cloudentity.edge.plugin.config._
import com.cloudentity.edge.domain.http._
import com.cloudentity.edge.domain.flow._
import com.cloudentity.edge.plugin.verticle.RequestPluginVerticle
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.parser._
import com.cloudentity.tools.vertx.http.{SimpleSmartHttpClient, SmartHttpClient}
import io.vertx.core.buffer.Buffer
import com.cloudentity.edge.commons.ClientWithTracing._
import codecs._
import com.google.common.cache.CacheBuilder
import com.cloudentity.edge.domain.flow.PluginName
import com.cloudentity.edge.domain.http.{ApiResponse, TargetRequest}
import com.cloudentity.edge.plugin.RequestPluginService
import com.cloudentity.tools.vertx.http.Headers

import scala.concurrent.Future

case class AuthnEdgePluginCacheConf(ttl: Long, keyHeaders: Option[List[String]])

/**
  * Delegates plugin application to Cloud Api Gateway.
  *
  * Configuration:
  * {
  *   "http": ...
  *   "cache": {
  *     "ttl": 10000,
  *     "keyHeaders": ["Authorization", "token"]
  *   }
  * }
  *
  * `http` configuration of SimpleSmartHttpClient
  * `cache` optional, no caching if not defined
  * `cache.ttl` time-to-live of entry in cache in millis
  * `cache.keyHeaders` defines what header values are used in cache key, optional, defaults to ["Authorization", "token"]
  *
  * Cache key is made of instance of AuthnPluginConf and pairs of header-name and corresponding header-value from request,
  * header-names are defined in `cache.keyHeaders`.
  */
class AuthnEdgePlugin extends RequestPluginVerticle[AuthnPluginConf] with RequestPluginService {
  override def name: PluginName = PluginName("authn")

  type Headers = Map[String, String]
  val defaultKeyHeaders = List("Authorization", "token")

  var client: SmartHttpClient = _
  var cacheOpt: Option[AuthnEdgePluginCache] = _

  override def initService(): Unit = {
    client = SimpleSmartHttpClient.create(vertx, getConfig().getJsonObject("http")).toTry.get
    cacheOpt = readCacheConfig().toTry.get.map(new AuthnEdgePluginCache(_))
  }

  private def readCacheConfig(): Either[io.circe.Error, Option[AuthnEdgePluginCacheConf]] =
    Option(getConfig.getJsonObject("cache")) match {
      case Some(cacheConf) => decode[AuthnEdgePluginCacheConf](cacheConf.toString).map(Some(_))
      case None => Right(None)
    }

  override def apply(ctx: RequestCtx, conf: AuthnPluginConf): Future[RequestCtx] =
    cacheOpt match {
      case Some(cache) =>
        cache.get(ctx.request, conf) match {
          case Some(cachedResponse) =>
            Future.successful(resultToRequestCtx(ctx, cachedResponse))
          case None =>
            callCloudAuthnPlugin(ctx, conf)
              .map { result =>
                cache.put(ctx.request, conf, result)
                resultToRequestCtx(ctx, result)
              }
        }
      case None =>
        callCloudAuthnPlugin(ctx, conf).map {
          case Right(flowCtx) => setAuthnCtx(ctx, flowCtx)
          case Left(response) => ctx.abort(response)
        }
    }

  def resultToRequestCtx(ctx: RequestCtx, result: Either[ApiResponse, List[FlowCtx]]): RequestCtx =
    result match {
      case Right(flowCtx) => setAuthnCtx(ctx, flowCtx)
      case Left(response) => ctx.abort(response)
    }

  def setAuthnCtx(ctx: RequestCtx, flowCtx: List[FlowCtx]): RequestCtx =
    flowCtx.foldLeft(ctx) { case (acc, FlowCtx(name, value)) => acc.withAuthnCtx(name, value) }

  def callCloudAuthnPlugin(ctx: RequestCtx, conf: AuthnPluginConf): Future[Either[ApiResponse, List[FlowCtx]]] = {
    val payload =
      AuthnProxyPluginRequest(
        request = AuthnTargetRequest(ctx.request.headers.toMap),
        conf    = conf
      )

    val path = Option(getConfig().getString("path")).getOrElse("")
    client.post(path).withTracing(ctx.tracingCtx).endWithBody(ctx.tracingCtx, payload.asJson.noSpaces).toScala()
      .flatMap { resp =>
        log.debug(ctx.tracingCtx, s"Received response from Cloud AuthzPlugin. code=${resp.getHttp.statusCode()}, body=${resp.getBody}")
        if (resp.getHttp.statusCode() == 200) {
          decode[AuthnProxyPluginResponse](resp.getBody.toString) match {
            case Right(authnResp) =>
              Future.successful(Right(authnResp.ctx))
            case Left(ex) =>
              log.error(ctx.tracingCtx,"Could not decode AuthnProxyPluginResponse", ex)
              Future.successful(Left(ApiResponse(500, Buffer.buffer(), Headers())))
          }
        } else {
          Future.successful(Left(ApiResponse(resp.getHttp.statusCode(), resp.getBody, Headers.of("Content-Type" -> "application/json"))))
        }
      }
  }

  override def validate(conf: AuthnPluginConf): ValidateResponse = ValidateOk
  override def confDecoder: Decoder[AuthnPluginConf] = deriveDecoder
}

class AuthnEdgePluginCache(conf: AuthnEdgePluginCacheConf) {
  type Key = (Map[String, String], AuthnPluginConf)
  type Value = Either[ApiResponse, List[FlowCtx]]

  val defaultKeyHeaders = List("Authorization", "token")

  val cache = CacheBuilder.newBuilder()
    .expireAfterWrite(conf.ttl, TimeUnit.MILLISECONDS)
    .build[Key, Value]()

  def get(req: TargetRequest, pluginConf: AuthnPluginConf): Option[Value] = {
    val key = (getKeyHeaders(req), pluginConf)
    Option(cache.getIfPresent(key))
  }

  def put(req: TargetRequest, pluginConf: AuthnPluginConf, value: Value): Unit = {
    val key = (getKeyHeaders(req), pluginConf)
    cache.put(key, value)
  }

  private def getKeyHeaders(req: TargetRequest): Map[String, String] =
    conf.keyHeaders.getOrElse(defaultKeyHeaders)
      .foldLeft(Map[String, String]()) { case (acc, keyHeader) =>
        req.headers.get(keyHeader) match {
          case Some(headerValue) => acc + (keyHeader -> headerValue)
          case None              => acc
        }
      }
}
