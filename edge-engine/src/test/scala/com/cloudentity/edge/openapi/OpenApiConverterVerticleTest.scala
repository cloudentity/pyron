package com.cloudentity.edge.openapi

import com.cloudentity.edge.VertxSpec
import com.cloudentity.edge.domain._
import com.cloudentity.edge.domain.flow.{GroupMatchCriteria, PathPattern, PathPrefix, RewriteMethod, RewritePath}
import com.cloudentity.edge.domain.openapi.{BasePath, ConverterConf, Host, OpenApiDefaultsConf, OpenApiRule}
import com.cloudentity.edge.util.{FutureUtils, OpenApiTestUtils}
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.swagger.models.Scheme
import io.vertx.core.http.HttpMethod
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

import scala.collection.JavaConverters._


@RunWith(classOf[JUnitRunner])
class OpenApiConverterVerticleOpenTest extends WordSpec with MustMatchers with VertxSpec with FutureUtils with OpenApiTestUtils {

  val converter = new OpenApiConverterVerticle()

  "OpenApiConverter" should {

    "build target service path" in {
      val rule = OpenApiRule(HttpMethod.GET, sampleServiceId, GroupMatchCriteria.empty, PathPattern("/test"), PathPrefix(""), false, None, None, List(), Nil)
      rule.targetServicePath must be ("/test")
    }

    "build target service path with path prefix" in {
      val rule = OpenApiRule(HttpMethod.GET, sampleServiceId, GroupMatchCriteria.empty, PathPattern("/test"), PathPrefix("/service"), false, None, None, List(), Nil)
      rule.targetServicePath must be ("/service/test")
    }

    "build target service path with dropped path prefix" in {
      val rule = OpenApiRule(HttpMethod.GET, sampleServiceId, GroupMatchCriteria.empty, PathPattern("/test"), PathPrefix("/service"), true, None, None, List(), Nil)
      rule.targetServicePath must be ("/test")
    }

    "build target service path with rewrite path" in {
      val rule = OpenApiRule(HttpMethod.GET, sampleServiceId, GroupMatchCriteria.empty, PathPattern("/test"), PathPrefix(""), false, None, Some(RewritePath("/api/test")), List(), Nil)
      rule.targetServicePath must be ("/api/test")
    }

    "convert only apis defined in rules" in {
      val apiPath1 = "/test1"
      val apiPath2 = "/test2"
      val swaggerBasePath = "/sla"

      val paths = Map(apiPath1 -> multiPath(), apiPath2 -> sampleGetPath(), "/notexposed" -> sampleGetPath())
      val swagger = sampleSwagger(swaggerBasePath, paths)

      val rules = List(
        OpenApiRule(HttpMethod.GET, sampleServiceId, GroupMatchCriteria.empty, PathPattern(apiPath1), PathPrefix(swaggerBasePath), false, None, None, List(), Nil),
        OpenApiRule(HttpMethod.POST, sampleServiceId, GroupMatchCriteria.empty, PathPattern(apiPath1), PathPrefix(swaggerBasePath), false, None, None, List(), Nil),
        OpenApiRule(HttpMethod.GET, sampleServiceId, GroupMatchCriteria.empty, PathPattern(apiPath2), PathPrefix(swaggerBasePath), false, None, None, List(), Nil)
      )

      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, swagger, rules, ConverterConf(None, None)))
      println(io.swagger.util.Json.pretty(result))

      result.getBasePath must be ("/api")
      result.getPaths.size() must be(2)

      val expectedPath1 = swaggerBasePath + apiPath1
      val path1 = result.getPath(expectedPath1)
      path1.getOperations.size() must be (2)

      val expectedPath2 = swaggerBasePath + apiPath2
      val path2 = result.getPath(expectedPath2)
      path2.getOperations.size() must be (1)
    }

    "convert api when rule with path rewrite" in {
      // given
      val targetSwaggerBasePath = "/overlay/api"
      val paths = Map("/users" -> samplePostPath())
      val targetSwagger = sampleSwagger(targetSwaggerBasePath, paths)

      val rules = List(
        OpenApiRule(
          method         = HttpMethod.POST,
          serviceId      = sampleServiceId,
          group          = GroupMatchCriteria.empty,
          pathPrefix     = PathPrefix("/apigw"),
          pathPattern    = PathPattern("/other-users"),
          dropPathPrefix = false,
          rewriteMethod  = None,
          rewritePath    = Some(RewritePath("/overlay/api/users")),
          plugins        = List(),
          tags           = Nil
        )
      )

      // when
      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, targetSwagger, rules, ConverterConf(None, None)))
      println(io.swagger.util.Json.pretty(result))

      // then
      result.getBasePath must be ("/api")

      result.getPaths.size() must be(1)
      result.getPath("/apigw/other-users") must not be (null)
    }

    "convert api when rule with dropped path prefix" in {
      // given
      val targetSwaggerBasePath = ""
      val paths = Map("/users" -> samplePostPath())
      val targetSwagger = sampleSwagger(targetSwaggerBasePath, paths)

      val rules = List(
        OpenApiRule(
          method         = HttpMethod.POST,
          serviceId      = sampleServiceId,
          group          = GroupMatchCriteria.empty,
          pathPrefix     = PathPrefix("/apigw"),
          pathPattern    = PathPattern("/users"),
          dropPathPrefix = true,
          rewriteMethod  = None,
          rewritePath    = None,
          plugins        = List(),
          tags           = Nil
        )
      )

      // when
      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, targetSwagger, rules, ConverterConf(None, None)))
      println(io.swagger.util.Json.pretty(result))

      // then
      result.getBasePath must be ("/api")

      result.getPaths.size() must be(1)
      result.getPath("/apigw/users") must not be (null)
    }

    "convert api when rule with method rewrite" in {
      // given
      val targetSwaggerBasePath = ""
      val paths = Map("/users" -> samplePostPath())
      val targetSwagger = sampleSwagger(targetSwaggerBasePath, paths)

      val rules = List(
        OpenApiRule(
          method         = HttpMethod.PUT,
          serviceId      = sampleServiceId,
          group          = GroupMatchCriteria.empty,
          pathPrefix     = PathPrefix(""),
          pathPattern    = PathPattern("/users"),
          dropPathPrefix = false,
          rewriteMethod  = Some(RewriteMethod(HttpMethod.POST)),
          rewritePath    = None,
          plugins        = List(),
          tags           = Nil
        )
      )

      // when
      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, targetSwagger, rules, ConverterConf(None, None)))
      println(io.swagger.util.Json.pretty(result))

      // then
      result.getBasePath must be ("/api")

      result.getPaths.size() must be(1)
      result.getPath("/users") must not be (null)
      result.getPath("/users").getPut must not be (null)
    }

    "set host, basePath and scheme based on config" in {
      val getPath = sampleGetPath()
      val targetServiceSwagger = sampleSwagger("/", Map("/test" -> getPath))
      val apiGwGetPath = PathPattern("/test")
      val rules = List(OpenApiRule(HttpMethod.GET, sampleServiceId, GroupMatchCriteria.empty, apiGwGetPath, PathPrefix(""), false, None, None, List(), Nil))

      val conf = ConverterConf(Some(OpenApiDefaultsConf(Some(Host("example.com")), Some(BasePath("/api")), Some(true))), None)
      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, targetServiceSwagger, rules, conf))

      println(io.swagger.util.Json.pretty(result))

      result.getHost must be ("example.com")
      result.getBasePath must be ("/api")
      result.getSchemes.asScala must be (List(Scheme.HTTPS))

      result.getPaths.size() must be (1)
      result.getPath("/test") must be (getPath)
    }

    "resolve operationId conflicts when rules proxy to the same api" in {
      val postPath = samplePostPath()
      val targetServiceSwagger = sampleSwagger("/", Map("/applications" -> postPath))

      val rules = List(
        OpenApiRule(HttpMethod.POST, sampleServiceId, GroupMatchCriteria.empty, PathPattern("/applications"), PathPrefix(""), false, None, None, List(), Nil),
        OpenApiRule(HttpMethod.POST, sampleServiceId, GroupMatchCriteria.empty, PathPattern("/admin/applications"), PathPrefix(""), false, None, Some(RewritePath("/applications")), List(), Nil)
      )

      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, targetServiceSwagger, rules, ConverterConf(None, None)))
      println(io.swagger.util.Json.pretty(result))

      result.getPaths.size must be (2)
      result.getPath("/applications").getPost.getOperationId must be ("postApplications")
      result.getPath("/admin/applications").getPost.getOperationId must be ("postAdminApplications")
    }

    "convert api when rule with drop prefix by adding prefix in generated swagger" in {
      val swagger = sampleSwagger("/", Map("/policy" -> samplePostPath()))

      val rules = List(OpenApiRule(HttpMethod.POST, sampleServiceId, GroupMatchCriteria.empty, PathPattern("/policy"), PathPrefix("/authz"), true, None, None, List(), Nil))
      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, swagger, rules, ConverterConf(None, None)))

      result.getBasePath must be ("/api")
      result.getPaths.size() must be (1)
      result.getPath("/authz/policy") must not be (null)
    }

    "convert api when rules with rewrite path proxying to generic api in target service" in {
      val swagger = sampleSwagger("/", Map("/relations/{relationType}/{relationValue}" -> samplePostPath()))

      val rules = List(OpenApiRule(HttpMethod.POST, sampleServiceId, GroupMatchCriteria.empty, PathPattern("/device/trust"), PathPrefix(""), false, None, Some(RewritePath("/relations/trust/trusted")), List(), Nil))
      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, swagger, rules, ConverterConf(None, None)))

      result.getBasePath must be ("/api")
      result.getPaths.size() must be (1)

      println(io.swagger.util.Json.pretty(result))
    }

    "convert api when rules with rewrite path and path params proxying to generic api in target service" in {
      val swagger = sampleSwagger("/", Map("/relations/device/{deviceUuid}/{relationType}" -> samplePostPath()))

      val rules = List(OpenApiRule(HttpMethod.POST, sampleServiceId, GroupMatchCriteria.empty, PathPattern("/devices/{deviceUuid}/trust"), PathPrefix(""), false, None, Some(RewritePath("/relations/device/{deviceUuid}/trust")), List(), Nil))
      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, swagger, rules, ConverterConf(None, None)))

      println(io.swagger.util.Json.pretty(result))

      result.getBasePath must be ("/api")
      result.getPaths.size() must be (1)

    }

    "convert rule with path variables mismatch" in {
      val targetServiceSwagger = sampleSwagger("/", Map("/application/{uuid}/capability/oauthClient" -> samplePostPath()))
      val apiGwGetPath = PathPattern("/application/{applicationId}/capability/oauthClient")

      val rules = List(OpenApiRule(HttpMethod.POST, sampleServiceId, GroupMatchCriteria.empty, apiGwGetPath, PathPrefix(""), false, None, None, List(), Nil))
      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, targetServiceSwagger, rules, ConverterConf(None, None)))

      println(io.swagger.util.Json.pretty(result))

      result.getBasePath must be ("/api")
      result.getPaths.size() must be (1)
    }

  }

}
