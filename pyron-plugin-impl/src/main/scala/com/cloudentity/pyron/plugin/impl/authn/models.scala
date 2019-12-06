package com.cloudentity.pyron.plugin.impl.authn

import com.cloudentity.pyron.plugin.impl.authn.AuthnPlugin.{AuthnEntityType, AuthnMethodName}
import com.nimbusds.jose.{JWSAlgorithm, JWSObject}
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.{JWSVerificationKeySelector, SecurityContext}
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTProcessor

import scala.util.Try

case class AuthnPluginConf(
  methods: List[AuthnMethodName],
  entities: Option[List[AuthnEntityType]],
  optionalEntities: Option[List[AuthnEntityType]],
  tokenHeader: Option[String],
  ctxKey: Option[String]
)

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