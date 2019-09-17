package com.cloudentity.edge.plugin.impl.authn.methods

import com.cloudentity.services.lla.client.api.UserApiClient
import com.cloudentity.services.openapi.tools.httpclient.vertxscala.auth.JwtAuth
import com.cloudentity.edge.api.Responses
import com.cloudentity.edge.api.Responses.{Error, Errors}
import com.cloudentity.edge.domain.flow.RequestCtx
import com.cloudentity.edge.plugin.impl.authn.HmacHelper
import scalaz.\/
import com.cloudentity.edge.plugin.impl.authn.codecs._


class LegacyHmacProvider extends AbstractHmacProvider {

  var legacyHmacProviderConf: LegacyHmacProviderConf = _

  override def initService(): Unit = {
    legacyHmacProviderConf = decodeConfigUnsafe[LegacyHmacProviderConf]
    baseHmacProviderConf = legacyHmacProviderConf
    realm = legacyHmacProviderConf.realm.getOrElse("internal-applications")
    hmac = new HmacHelper(legacyHmacProviderConf.dateFormat, legacyHmacProviderConf.limitInMinutes, legacyHmacProviderConf.authHeaderPrefix)
    userApi = createClient(classOf[UserApiClient])
    jwtAuthenticator = createClient(classOf[JwtAuth])
  }

  def evaluateAcceptableHmacSignatures(ctx: RequestCtx, apiKey: String, data: HmacData): \/[Responses.Error, List[String]] =
    for {
      encodedRequest <- \/.fromEither(ctx.request.headers.get(legacyHmacProviderConf.request).toRight[Error](Errors.invalidRequest))
      request <- hmac.decode(encodedRequest)
      hmacSignature <- hmac.buildSignature(apiKey.getBytes(), request.getBytes())
    } yield List(hmacSignature)

}

case class LegacyHmacProviderConf(authorization: String, request: String, date: String, key: String, authHeaderPrefix: String,
                                dateFormat: String, limitInMinutes: Int, realm: Option[String], apiKeyEncryptionKey: Option[String]) extends BaseHmacProviderConf