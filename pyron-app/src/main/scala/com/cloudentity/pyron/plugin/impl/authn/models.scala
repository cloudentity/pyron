package com.cloudentity.pyron.plugin.impl.authn

import io.circe.Json
import com.cloudentity.pyron.plugin.impl.authn.AuthnPlugin.{AuthnEntityType, AuthnMethodName}
import com.nimbusds.jose.{JWSAlgorithm, JWSObject}
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.{JWSVerificationKeySelector, SecurityContext}
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTProcessor

import scala.util.Try

case class FlowCtx(name: String, value: Json)
case class AuthnPluginConf(
  methods: List[AuthnMethodName],
  entities: Option[List[AuthnEntityType]],
  optionalEntities: Option[List[AuthnEntityType]],
  tokenHeader: Option[String],
  ctxKey: Option[String]
)
case class AuthnProxyPluginResponse(ctx: List[FlowCtx])

case class AuthnTargetRequest(headers: Map[String, List[String]])
case class AuthnProxyPluginRequest(request: AuthnTargetRequest, conf: AuthnPluginConf)

object OAuthAccessToken {
  def parse(token: String, keySet: JWKSet): Either[Throwable, JWTClaimsSet] = {
    def alg: JWSAlgorithm = Try(JWSObject.parse(token).getHeader.getAlgorithm).getOrElse {
      JWSAlgorithm.RS256
    }
    val jwtProcessor = new DefaultJWTProcessor[SecurityContext]()
    val keySelector = new JWSVerificationKeySelector(alg, new ImmutableJWKSet[SecurityContext](keySet))
    jwtProcessor.setJWSKeySelector(keySelector)
    Try(jwtProcessor.process(token, null)).toEither
  }
}