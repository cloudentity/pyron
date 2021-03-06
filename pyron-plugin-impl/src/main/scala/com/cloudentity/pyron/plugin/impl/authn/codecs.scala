package com.cloudentity.pyron.plugin.impl.authn

import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}
import io.circe.generic.semiauto._
import io.circe.syntax._
import com.cloudentity.pyron.plugin.impl.authn.methods._

object codecs {
  implicit lazy val OpenApiOauth2FlowDec: Decoder[OpenApiOauth2Flow] = Decoder.decodeString.emap {
    case "implicit"          => Right(ImplicitFlow)
    case "authorizationCode" => Right(AuthorizationCodeFlow)
    case "password"          => Right(PasswordGrantFlow)
    case "clientCredentials" => Right(ClientCredentialsFlow)
    case flow                => Left(s"Unsupported flow $flow for openapi oauth2 security definition")
  }

  implicit lazy val OpenApiOauth2FlowEnc: Encoder[OpenApiOauth2Flow] = Encoder.encodeString.contramap(_.name)

  implicit lazy val Oauth2SecurityDefinitionConfDec = deriveDecoder[Oauth2SecurityDefinitionConf]
  implicit lazy val Oauth2SecurityDefinitionConfEnc = deriveEncoder[Oauth2SecurityDefinitionConf]

  implicit lazy val BasicSecurityDefinitionConfDec = deriveDecoder[BasicSecurityDefinitionConf]
  implicit lazy val BasicSecurityDefinitionConfEnc = deriveEncoder[BasicSecurityDefinitionConf]


  implicit lazy val ApiKeyInDec: Decoder[ApiKeyIn] = Decoder.decodeString.emap {
    case "header"            => Right(Header)
    case "query"             => Right(Query)
    case in                => Left(s"Unsupported api key securoty definiton location $in")
  }

  implicit lazy val ApiKeyInEnc: Encoder[ApiKeyIn] = Encoder.encodeString.contramap(_.value)

  implicit lazy val ApiKeySecurityDefinitionConfDec = deriveDecoder[ApiKeySecurityDefinitionConf]
  implicit lazy val ApiKeySecurityDefinitionConfEnc = deriveEncoder[ApiKeySecurityDefinitionConf]

  implicit lazy val OpenApiSecurityDefinitionConfDec = new Decoder[OpenApiSecurityDefinitionConf] {
    override def apply(c: HCursor): Result[OpenApiSecurityDefinitionConf] = {
      c.downField("type").as[String].flatMap {
        case "oauth2" => c.as[Oauth2SecurityDefinitionConf]
        case "basic" => c.as[BasicSecurityDefinitionConf]
        case "apiKey" => c.as[ApiKeySecurityDefinitionConf]
        case t => Left(DecodingFailure(s"Unsupported type $t for openapi security definition", c.history))
      }
    }
  }

  implicit lazy val OpenApiSecurityDefinitionConfEnc = Encoder.encodeJson.contramap[OpenApiSecurityDefinitionConf] {
    case secDef: Oauth2SecurityDefinitionConf => secDef.asJsonObject.add("type", secDef.`type`.asJson).asJson
    case secDef: BasicSecurityDefinitionConf  => secDef.asJsonObject.add("type", secDef.`type`.asJson).asJson
    case secDef: ApiKeySecurityDefinitionConf => secDef.asJsonObject.add("type", secDef.`type`.asJson).asJson
  }

  implicit lazy val OauthUrlEnc = deriveEncoder[OauthUrl]
  implicit lazy val OauthUrlDec = deriveDecoder[OauthUrl]

  implicit lazy val OpenApiOauthConfEnc = deriveEncoder[OpenApiOauthUrlsConf]
  implicit lazy val OpenApiOauthConfDec = deriveDecoder[OpenApiOauthUrlsConf]

  implicit lazy val AuthnApiOpenApiConfEnc = deriveEncoder[AuthnApiOpenApiConf]
  implicit lazy val AuthnApiOpenApiConfDec = deriveDecoder[AuthnApiOpenApiConf]

  implicit lazy val AuthnPluginConfEnc = deriveEncoder[AuthnPluginConf]
  implicit lazy val AuthnPluginConfDec = deriveDecoder[AuthnPluginConf]

  implicit lazy val AuthnPluginVerticleConfEnc = deriveEncoder[AuthnPluginVerticleConf]
  implicit lazy val AuthnPluginVerticleConfDec = deriveDecoder[AuthnPluginVerticleConf]
}
