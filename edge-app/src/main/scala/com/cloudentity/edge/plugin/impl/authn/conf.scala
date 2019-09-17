package com.cloudentity.edge.plugin.impl.authn

case class AuthnPluginVerticleConf(
  methodsMapping: Map[String, String],
  entitiesMapping: Map[String, Map[String, String]],
  authnMethodProvidersConfigKey: Option[String],
  authnEntityProvidersConfigKey: Option[String],
  openApi: Option[AuthnApiOpenApiConf]
)

case class AuthnMethodConf(tokenHeader: Option[String])

case class AuthnApiOpenApiConf(
  oauthUrls: Option[OpenApiOauthUrlsConf],
  securityDefinitionsMapping: Map[String, OpenApiSecurityDefinitionConf]
)

case class OpenApiOauthUrlsConf(
  authorizationUrl: OauthUrl,
  tokenUrl: OauthUrl
)

case class OauthUrl(
  host: String,
  path: String,
  port : Int,
  ssl : Boolean
) {
  def asString: String =  {
    val portS =
      if ((ssl && port != 443) || (!ssl && port != 80))
        ":"+ port
      else
        ""
    s"http${if(ssl) "s" else ""}://$host$portS$path"
  }
}

sealed trait OpenApiSecurityDefinitionConf {
  def `type`: String
}
case class Oauth2SecurityDefinitionConf(
 flows: List[OpenApiOauth2Flow]
 //  scopes: Map[String, String]
) extends OpenApiSecurityDefinitionConf {
  override def `type`: String = "oauth2"
}

case class BasicSecurityDefinitionConf(
  ) extends OpenApiSecurityDefinitionConf {
  override def `type`: String = "basic"
}

case class ApiKeySecurityDefinitionConf(
 definitionName: String,
 in: ApiKeyIn,
 name: String

) extends OpenApiSecurityDefinitionConf {
  override def `type`: String = "apiKey"
}

sealed trait OpenApiOauth2Flow {
  def name: String
  def openApiFlowName: String
}
case object ImplicitFlow extends OpenApiOauth2Flow {
  override def name: String = "implicit"
  override def openApiFlowName: String = "implicit"
}

case object AuthorizationCodeFlow extends OpenApiOauth2Flow {
  override def name: String = "authorizationCode"
  override def openApiFlowName: String = "accessCode"
}

case object PasswordGrantFlow extends OpenApiOauth2Flow {
  override def name: String = "password"
  override def openApiFlowName: String = "password"
}

case object ClientCredentialsFlow extends OpenApiOauth2Flow {
  override def name: String = "clientCredentials"
  override def openApiFlowName: String = "application"
}

sealed trait ApiKeyIn {
  def value: String
}

case object Header extends ApiKeyIn {
  override def value: String = "header"
}

case object Query extends ApiKeyIn {
  override def value: String = "query"
}