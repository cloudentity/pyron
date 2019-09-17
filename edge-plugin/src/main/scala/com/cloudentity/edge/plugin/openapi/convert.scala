package com.cloudentity.edge.plugin.openapi

import com.cloudentity.edge.domain.flow.PluginConf
import com.cloudentity.edge.domain.openapi.OpenApiRule
import io.swagger.models.Swagger

case class ConvertOpenApiRequest(swagger: Swagger, rule: OpenApiRule, conf: PluginConf)

sealed trait ConvertOpenApiResponse
  case class ConvertedOpenApi(swagger: Swagger) extends ConvertOpenApiResponse
  case class ConvertOpenApiFailure(msg: String) extends ConvertOpenApiResponse
  case class ConvertOpenApiError(msg: String) extends ConvertOpenApiResponse


