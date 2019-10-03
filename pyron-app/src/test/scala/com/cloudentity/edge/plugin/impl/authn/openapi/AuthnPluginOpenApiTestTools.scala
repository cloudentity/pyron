package com.cloudentity.pyron.plugin.impl.authn.openapi

import io.circe.syntax._
import com.cloudentity.pyron.plugin.openapi.{ConvertOpenApiResponse, ConvertedOpenApi}
import com.cloudentity.pyron.domain.flow.{GroupMatchCriteria, PathPattern, PathPrefix, PluginConf, PluginName, TargetHost}
import com.cloudentity.pyron.domain.openapi.{OpenApiRule, StaticServiceId}
import com.cloudentity.pyron.plugin.impl.authn.codecs._
import com.cloudentity.pyron.plugin.impl.authn.openapi.AuthnPluginOpenApiConverter.convert
import com.cloudentity.pyron.plugin.impl.authn.{AuthnApiOpenApiConf, AuthnPluginConf}
import com.cloudentity.pyron.util.OpenApiTestUtils
import io.swagger.models.{Operation, Path, Swagger}
import io.vertx.core.http.HttpMethod
import org.scalatest.{Matchers, WordSpec}

object AuthnPluginOpenApiTestTools extends WordSpec with Matchers with OpenApiTestUtils {

  case class SimpleTestEndpoint(
     path: String,
     method: HttpMethod
   )

  def convertMultipleEndpoints(swagger: Swagger, endpoints: List[(OpenApiRule, AuthnPluginConf)], pluginConf: AuthnApiOpenApiConf): List[ConvertOpenApiResponse] = {
    endpoints.headOption.map(ruleConf => {
      val headResp = convert(swagger, ruleConf._1, ruleConf._2, pluginConf)
      headResp match {
        case ConvertedOpenApi(_) => endpoints.tail.foldLeft(List(headResp)) {
          (list, tup) => {
            list.last match {
              case ConvertedOpenApi(swag) => list :+ convert(swag, tup._1, tup._2, pluginConf)
              case e                      => fail(s"Could not convert swagger $e")
            }
          }
        }
        case e => fail(s"Could not convert swagger $e")
      }
    }).getOrElse(List())
  }

  def apiRule(endpoint: SimpleTestEndpoint, endpointConf: AuthnPluginConf): OpenApiRule = {
    val jsonPluginConf =  PluginConf(PluginName("authn"), endpointConf.asJson)
    OpenApiRule(
      endpoint.method,
      StaticServiceId(TargetHost(""), 80, ssl = false),
      GroupMatchCriteria.empty,
      PathPattern(endpoint.path),
      PathPrefix("/some/prefix"),
      dropPathPrefix = false,
      None,
      None,
      List(jsonPluginConf),
      Nil
    )
  }

  def swaggerWithEndpoints(endpoints: List[SimpleTestEndpoint]): Swagger= {
    sampleSwagger("/some/prefix", endpoints.map(
      e => (e.path, new Path().set(e.method.name.toLowerCase, new Operation()))).toMap
    )
  }

  def singleEndpointParams(endpointConf: AuthnPluginConf, pluginConf: AuthnApiOpenApiConf): (Swagger, OpenApiRule, AuthnPluginConf, AuthnApiOpenApiConf) = {
    val endpoint = SimpleTestEndpoint("/test", HttpMethod.GET)
    (swaggerWithEndpoints(List(endpoint)), apiRule(endpoint, endpointConf), endpointConf, pluginConf)
  }

  def convertWithParams(t: (Swagger, OpenApiRule, AuthnPluginConf, AuthnApiOpenApiConf)): ConvertOpenApiResponse = {
    (convert _).tupled(t)
  }

  def convertWithSingleGetEndpoint(endpointConf: AuthnPluginConf, pluginConf: AuthnApiOpenApiConf): ConvertOpenApiResponse = {
    convertWithParams(singleEndpointParams(endpointConf, pluginConf))
  }


}
