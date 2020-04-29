package com.cloudentity.pyron.plugin.openapi

import com.cloudentity.pyron.domain.flow.{PluginConf, ApiGroupPluginConf}
import com.cloudentity.pyron.domain.openapi.OpenApiRule
import io.swagger.models.Swagger

case class ConvertOpenApiRequest(swagger: Swagger, rule: OpenApiRule, conf: ApiGroupPluginConf)

sealed trait ConvertOpenApiResponse
  case class ConvertedOpenApi(swagger: Swagger) extends ConvertOpenApiResponse
  case class ConvertOpenApiFailure(msg: String) extends ConvertOpenApiResponse
  case class ConvertOpenApiError(msg: String) extends ConvertOpenApiResponse


