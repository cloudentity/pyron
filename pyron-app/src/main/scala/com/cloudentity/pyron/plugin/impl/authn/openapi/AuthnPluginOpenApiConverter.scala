package com.cloudentity.pyron.plugin.impl.authn.openapi

import com.cloudentity.pyron.domain.openapi.OpenApiRule
import com.cloudentity.pyron.plugin.openapi._
import com.cloudentity.pyron.openapi.OpenApiPluginUtils
import com.cloudentity.pyron.plugin.impl.authn._
import io.swagger.models.auth.{ApiKeyAuthDefinition, In, OAuth2Definition}
import io.swagger.models.parameters.HeaderParameter
import io.swagger.models.{Operation, Swagger}
import io.vertx.core.logging.LoggerFactory
import scalaz.Scalaz._

import scala.collection.JavaConverters._

object AuthnPluginOpenApiConverter extends OpenApiPluginUtils {

  val log = LoggerFactory.getLogger(this.getClass)

  case class AppendedSecurityDefinition(
    key: String
  )

  def convert(openApi: Swagger, rule: OpenApiRule, endpointConf: AuthnPluginConf, openApiPluginConf: AuthnApiOpenApiConf): ConvertOpenApiResponse = {
    findOperation(openApi, rule) match {
      case Some(op) => {
        endpointConf.tokenHeader match {
          case Some(tokenHeader) =>
            setTokenHeaderParam(op, tokenHeader, endpointConf)
          case None =>
            setSecurityDefinitions(openApi, endpointConf, openApiPluginConf, op)
        }

        openApi
      } |> ConvertedOpenApi
      case None => ConvertedOpenApi(openApi)
    }
  }

  private def setTokenHeaderParam(op: Operation, tokenHeader: String, endpointConf: AuthnPluginConf): Unit = {
    val param =
      new HeaderParameter()
        .name(tokenHeader)
        .required(false)
        .`type`("string")
        .description(s"Contains extra token used to build authentication context. Supported token types: [${endpointConf.methods.filterNot(_ == "anonymous").mkString(", ")}]")

    op.getParameters.add(param)
  }

  private def setSecurityDefinitions(openApi: Swagger, endpointConf: AuthnPluginConf, openApiPluginConf: AuthnApiOpenApiConf, op: Operation): Unit = {
    val matchingDefsNames = addSecurityDefinitions(openApi, endpointConf, openApiPluginConf)
    addSecuritySchemes(op, matchingDefsNames)
  }

  def addSecurityDefinitions(swagger: Swagger, endpointConf: AuthnPluginConf, openApiPluginConf: AuthnApiOpenApiConf): List[AppendedSecurityDefinition] = {

    openApiPluginConf.securityDefinitionsMapping
        .filterKeys(method => endpointConf.methods.contains(method))
        .foldLeft(List[AppendedSecurityDefinition]()) {
          case (list, (_, oauthConf: Oauth2SecurityDefinitionConf)) => list ++ appendOauthSecurityDefinitions(swagger, oauthConf, openApiPluginConf.oauthUrls)
          case (list, (_, apiKeyConf: ApiKeySecurityDefinitionConf)) => list :+ appendApiKeySecurityDefinition(swagger, apiKeyConf)
          case (list, _) => list
        }
  }

  private def appendOauthSecurityDefinitions(swagger: Swagger, oauthConf: Oauth2SecurityDefinitionConf, oauthUrlsConf: Option[OpenApiOauthUrlsConf]): List[AppendedSecurityDefinition] = {
    val flowsByKey: Map[AppendedSecurityDefinition, OpenApiOauth2Flow] = oauthConf.flows
      .map(flow => (AppendedSecurityDefinition(s"oauth2_${flow.name}"), flow)).toMap

    for ((appendedDef, flow) <- flowsByKey) {
      appendOauthSecurityDefinition(swagger, appendedDef.key, flow, oauthUrlsConf)
    }

    flowsByKey.keys.toList
  }

  private def appendOauthSecurityDefinition(swagger: Swagger, keyName: String, flow: OpenApiOauth2Flow, oauthUrlsConf: Option[OpenApiOauthUrlsConf]) = {
    swagger.securityDefinition(keyName, {
      val oauthDef = new OAuth2Definition()
      oauthDef.setFlow(flow.openApiFlowName)
      if (oauthUrlsConf.isDefined) {
        val urls = oauthUrlsConf.get
        flow match {
          case ImplicitFlow | AuthorizationCodeFlow =>
            oauthDef.setAuthorizationUrl(urls.authorizationUrl.asString)
          case _ =>
        }
        flow match {
          case PasswordGrantFlow | AuthorizationCodeFlow | ClientCredentialsFlow =>
            oauthDef.setTokenUrl(urls.tokenUrl.asString)
          case _ =>
        }
      }

      oauthDef
    })
  }

  def appendApiKeySecurityDefinition(swagger: Swagger, apiKeyConf: ApiKeySecurityDefinitionConf): AppendedSecurityDefinition = {
    swagger.securityDefinition(apiKeyConf.definitionName, new ApiKeyAuthDefinition(apiKeyConf.name, In.forValue(apiKeyConf.in.value)))
    AppendedSecurityDefinition(apiKeyConf.definitionName)
  }


  def addSecuritySchemes(op: Operation, matchingDefinitions: List[AppendedSecurityDefinition])= {
    matchingDefinitions
      .foreach(definition => op.addSecurity(definition.key, List().asJava))
  }


}
