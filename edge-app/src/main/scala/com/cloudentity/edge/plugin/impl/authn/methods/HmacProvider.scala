package com.cloudentity.edge.plugin.impl.authn.methods

import java.nio.charset.Charset._

import com.cloudentity.services.lla.client.api.UserApiClient
import com.cloudentity.services.openapi.tools.httpclient.vertxscala.auth.JwtAuth
import com.cloudentity.edge.api.Responses
import com.cloudentity.edge.api.Responses.Errors
import com.cloudentity.edge.plugin.impl.authn.HmacHelper
import scalaz._
import Scalaz._
import com.cloudentity.edge.domain.flow.RequestCtx
import com.cloudentity.edge.plugin.impl.authn.codecs._

class HmacProvider extends AbstractHmacProvider {

  var hmacProviderConf: HmacProviderConf = _

  override def initService(): Unit = {
    hmacProviderConf = decodeConfigUnsafe[HmacProviderConf]
    baseHmacProviderConf = hmacProviderConf
    realm = hmacProviderConf.realm.getOrElse("internal-applications")
    hmac = new HmacHelper(hmacProviderConf.dateFormat, hmacProviderConf.limitInMinutes, hmacProviderConf.authHeaderPrefix)
    userApi = createClient(classOf[UserApiClient])
    jwtAuthenticator = createClient(classOf[JwtAuth])
  }

  def evaluateAcceptableHmacSignatures(ctx: RequestCtx, apiKey: String, data: HmacData): \/[Responses.Error, List[String]] = {
    val method =  ctx.original.method.toString
    val uri =     ctx.original.path.value.concat(if (ctx.original.queryParams.toMap.nonEmpty) "?" + ctx.original.queryParams else "")
    val body =    ctx.original.bodyOpt.map(_.toString(defaultCharset)).getOrElse("")

    val acceptableHostsWithOptionalBasePaths: Responses.Error \/ List[String] = hmacProviderConf.originalRequestSources match {
      case Some(requestSources) => \/-(requestSources.map(rS => rS.host + rS.port.map(":" + _).getOrElse("") + rS.pathPrefix.getOrElse("")))
      case None => ctx.original.headers.get("Host") match {
        case Some(host) => \/-(List(host))
        case None => {
          log.warn(ctx.tracingCtx, s"HMAC plugin is configured to get base host from Host header but header was not provided")
          -\/(Errors.invalidRequest)
        }
      }
    }

    acceptableHostsWithOptionalBasePaths match {
      case -\/(error) => -\/(error)
      case \/-(hostsWithOptionalBasePaths) => {
        hostsWithOptionalBasePaths.map { originalBasePath =>
          val url = buildBaseUrl(originalBasePath, uri)
            for {
            request <- hmac.buildRequest(method, body, data.date, url)
            hmacSignature <- hmac.buildSignature(apiKey.getBytes(), request.getBytes())
          } yield hmacSignature
        }.sequenceU
      }
    }
  }

  def buildBaseUrl(basePath: String, relativeUri: String): String = "http://" + basePath + relativeUri
}

case class HmacProviderConf(authorization: String, date: String, key: String, authHeaderPrefix: String,
                            dateFormat: String, limitInMinutes: Int, realm: Option[String], apiKeyEncryptionKey: Option[String],
                            originalRequestSources: Option[List[RequestSource]]) extends BaseHmacProviderConf

case class RequestSource(host: String, port: Option[Int], pathPrefix: Option[String])
