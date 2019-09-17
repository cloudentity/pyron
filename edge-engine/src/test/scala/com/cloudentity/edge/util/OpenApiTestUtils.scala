package com.cloudentity.edge.util

import com.cloudentity.edge.domain.flow.TargetHost
import com.cloudentity.edge.domain.openapi.{OpenApiRule, StaticServiceId}
import com.cloudentity.edge.plugin.openapi.{ConvertOpenApiResponse, ConvertedOpenApi}
import com.cloudentity.edge.openapi.OpenApiConverterUtils
import io.swagger.models._
import io.swagger.models.auth.SecuritySchemeDefinition
import org.scalatest.Assertions._
import org.scalatest.Inside._
import org.scalatest.matchers.Matcher
import org.scalatest.{Assertion, Succeeded}

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
      case ConvertedOpenApi(respSwagger) => com.cloudentity.edge.openapi.OpenApiPluginUtils.findOperation(respSwagger, rule)
        .getOrElse(fail(s"Could not find operation"))
      case e => fail(s"Failed to convert swagger $e")
    }

    def getSecurityDefinitions: mutable.Map[String, SecuritySchemeDefinition] = resp match {
      case ConvertedOpenApi(respSwagger) => respSwagger.getSecurityDefinitions.asScala
      case e => fail(s"Failed to convert swagger $e")
    }

    def securityDefinitionsShould(matcher: Matcher[mutable.Map[String, SecuritySchemeDefinition]]): Assertion =
      execMatcherOnResp(resp, matcher, _.getSecurityDefinitions.asScala)
  }

  def execMatcherOnResp[T](resp: ConvertOpenApiResponse, matcher: Matcher[T], extractor: Swagger => T): Assertion = {
    inside(resp) {
      case ConvertedOpenApi(respSwagger) => {
        val res = matcher.apply(extractor.apply(respSwagger))
        if (res.matches)
          Succeeded
        else
          fail(res.failureMessage)
      }
      case e => fail(s"Failed to convert Swagger $e")
    }
  }


}
