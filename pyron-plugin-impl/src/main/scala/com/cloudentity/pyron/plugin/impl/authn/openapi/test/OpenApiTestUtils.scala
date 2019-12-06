package com.cloudentity.pyron.plugin.impl.authn.openapi.test

import com.cloudentity.pyron.domain.flow.TargetHost
import com.cloudentity.pyron.domain.openapi.{OpenApiRule, StaticServiceId}
import com.cloudentity.pyron.openapi.OpenApiConverterUtils
import com.cloudentity.pyron.plugin.openapi.{ConvertOpenApiResponse, ConvertedOpenApi}
import io.swagger.models._
import io.swagger.models.auth.SecuritySchemeDefinition

import scala.collection.JavaConverters._
import scala.collection.mutable

trait OpenApiTestUtils extends OpenApiConverterUtils {

  val sampleServiceId = StaticServiceId(TargetHost("localhost"), 9999, false)

  def sampleSwagger(basePath: String, paths: Map[String, Path]): Swagger = {
    val swagger = new Swagger()
      .info(new Info().title("Sample service").version("1.0.0"))
      .basePath(basePath)
      .paths(paths.asJava)
    swagger
  }

  def getPath(method: String, operationId: String, desc: String, responseCode: Int, response: Response): Path = {
    val path = new Path()
    path.set(method, new Operation().operationId(operationId).description(desc).response(responseCode, response))
    path
  }

  def multiPath(): Path = {
    val path = new Path()
    path.setGet(new Operation().operationId("getOperation").description("get desc").response(200, new Response().description("ok")))
    path.setPost(new Operation().operationId("postOperation").description("get desc").response(200, new Response().description("ok")))
    path.setPut(new Operation().operationId("putOperation").description("put desc").response(200, new Response().description("ok")))
    path
  }

  def sampleGetPath(): Path = {
    getPath("get", "getApi", "get api desc", 200, new Response().description("success"))
  }

  def samplePostPath(): Path = {
    getPath("post", "postApi", "post api desc", 200, new Response().description("success"))
  }

  implicit class ConvertOpenApiResponseOps(resp: ConvertOpenApiResponse) {

    def getOperationByRule(rule: OpenApiRule): Operation = resp match {
      case ConvertedOpenApi(respSwagger) => com.cloudentity.pyron.openapi.OpenApiPluginUtils.findOperation(respSwagger, rule)
        .getOrElse(throw new Exception(s"Could not find operation"))
      case e => throw new Exception(s"Failed to convert swagger $e")
    }

    def getSecurityDefinitions: mutable.Map[String, SecuritySchemeDefinition] = resp match {
      case ConvertedOpenApi(respSwagger) => respSwagger.getSecurityDefinitions.asScala
      case e => throw new Exception(s"Failed to convert swagger $e")
    }

    def securityDefinitionsAssert(assertion: mutable.Map[String, SecuritySchemeDefinition] => Unit): Unit =
      execMatcherOnResp(resp, assertion, _.getSecurityDefinitions.asScala)
  }

  def execMatcherOnResp[T](resp: ConvertOpenApiResponse, assertion: T => Unit, extractor: Swagger => T): Unit =
    resp match {
      case ConvertedOpenApi(respSwagger) =>
        assertion.apply(extractor.apply(respSwagger))
      case e =>
        throw new Exception(s"Failed to convert Swagger $e")
  }
}