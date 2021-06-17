package com.cloudentity.pyron.openapi

import com.cloudentity.pyron.VertxSpec
import com.cloudentity.pyron.domain.flow.{GroupMatchCriteria, PathPattern, PathPrefix, RewriteMethod, RewritePath}
import com.cloudentity.pyron.domain.openapi.{BasePath, ConverterConf, Host, OpenApiDefaultsConf, OpenApiRule}
import com.cloudentity.pyron.util.{FutureUtils, OpenApiTestUtils}
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.swagger.models.{Scheme, Swagger}
import io.vertx.core.http.HttpMethod
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

import scala.collection.JavaConverters._


@RunWith(classOf[JUnitRunner])
class OpenApiConverterVerticleTest extends WordSpec with MustMatchers with VertxSpec with FutureUtils with OpenApiTestUtils {

  val converter = new OpenApiConverterVerticle()

  "OpenApiConverter" should {

    "build target service path" in {
      val rule = OpenApiRule(
        method = HttpMethod.GET,
        serviceId = sampleServiceId,
        group = GroupMatchCriteria.empty,
        pathPattern = PathPattern("/test"),
        pathPrefix = PathPrefix(""),
        dropPathPrefix = false,
        rewriteMethod = None,
        rewritePath = None,
        plugins = Nil,
        tags = Nil,
        operationId = None
      )
      rule.targetServicePath mustBe "/test"
    }

    "build target service path with path prefix" in {
      val rule = OpenApiRule(
        method = HttpMethod.GET,
        serviceId = sampleServiceId,
        group = GroupMatchCriteria.empty,
        pathPattern = PathPattern("/test"),
        pathPrefix = PathPrefix("/service"),
        dropPathPrefix = false,
        rewriteMethod = None,
        rewritePath = None,
        plugins = Nil,
        tags = Nil,
        operationId = None
      )
      rule.targetServicePath mustBe "/service/test"
    }

    "build target service path with dropped path prefix" in {
      val rule = OpenApiRule(
        method = HttpMethod.GET,
        serviceId = sampleServiceId,
        group = GroupMatchCriteria.empty,
        pathPattern = PathPattern("/test"),
        pathPrefix = PathPrefix("/service"),
        dropPathPrefix = true,
        rewriteMethod = None,
        rewritePath = None,
        plugins = Nil,
        tags = Nil,
        operationId = None
      )
      rule.targetServicePath mustBe "/test"
    }

    "build target service path with rewrite path" in {
      val rule = OpenApiRule(
        method = HttpMethod.GET,
        serviceId = sampleServiceId,
        group = GroupMatchCriteria.empty,
        pathPattern = PathPattern("/test"),
        pathPrefix = PathPrefix(""),
        dropPathPrefix = false,
        rewriteMethod = None,
        rewritePath = Some(RewritePath("/api/test")),
        plugins = Nil,
        tags = Nil,
        operationId = None
      )
      rule.targetServicePath mustBe "/api/test"
    }

    "convert only apis defined in rules" in {
      val apiPath1 = "/test1"
      val apiPath2 = "/test2"
      val apiPath3 = "/notexposed"
      val swaggerBasePath = "/sla"

      val paths = Map(apiPath1 -> multiPath(), apiPath2 -> sampleGetPath(), apiPath3 -> sampleGetPath())
      val swagger = sampleSwagger(swaggerBasePath, paths)

      val rules = List(
        OpenApiRule(
          method = HttpMethod.GET,
          serviceId = sampleServiceId,
          group = GroupMatchCriteria.empty,
          pathPattern = PathPattern(apiPath1),
          pathPrefix = PathPrefix(swaggerBasePath),
          dropPathPrefix = false,
          rewriteMethod = None,
          rewritePath = None,
          plugins = Nil,
          tags = Nil,
          operationId = None
        ),
        OpenApiRule(
          method = HttpMethod.POST,
          serviceId = sampleServiceId,
          group = GroupMatchCriteria.empty,
          pathPattern = PathPattern(apiPath1),
          pathPrefix = PathPrefix(swaggerBasePath),
          dropPathPrefix = false,
          rewriteMethod = None,
          rewritePath = None,
          plugins = Nil,
          tags = Nil,
          operationId = None
        ),
        OpenApiRule(
          method = HttpMethod.GET,
          serviceId = sampleServiceId,
          group = GroupMatchCriteria.empty,
          pathPattern = PathPattern(apiPath2),
          pathPrefix = PathPrefix(swaggerBasePath),
          dropPathPrefix = false,
          rewriteMethod = None,
          rewritePath = None,
          plugins = Nil,
          tags = Nil,
          operationId = None
        )
      )

      val result: Swagger = await(converter.convert(TracingContext.dummy(), sampleServiceId, swagger, rules, ConverterConf(None, None)))
      println(io.swagger.util.Json.pretty(result))

      result.getBasePath mustBe "/api"
      result.getPaths.size() mustBe 2

      val expectedPath1 = swaggerBasePath + apiPath1
      val path1 = result.getPath(expectedPath1)
      path1.getOperations.size() mustBe 2

      val expectedPath2 = swaggerBasePath + apiPath2
      val path2 = result.getPath(expectedPath2)
      path2.getOperations.size() mustBe 1

      val unexpectedPath = swaggerBasePath + apiPath3
      val path3 = result.getPath(unexpectedPath)
      path3 mustBe null
    }

    "convert api when rule with path rewrite" in {
      // given
      val targetSwaggerBasePath = "/overlay/api"
      val paths = Map("/users" -> samplePostPath())
      val targetSwagger = sampleSwagger(targetSwaggerBasePath, paths)

      val rules = List(
        OpenApiRule(
          method = HttpMethod.POST,
          serviceId = sampleServiceId,
          group = GroupMatchCriteria.empty,
          pathPattern = PathPattern("/other-users"),
          pathPrefix = PathPrefix("/apigw"),
          dropPathPrefix = false,
          rewriteMethod = None,
          rewritePath = Some(RewritePath("/overlay/api/users")),
          plugins = Nil,
          tags = Nil,
          operationId = None
        )
      )

      // when
      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, targetSwagger, rules, ConverterConf(None, None)))
      println(io.swagger.util.Json.pretty(result))

      // then
      result.getBasePath mustBe "/api"

      result.getPaths.size() mustBe 1
      result.getPath("/apigw/other-users") must not be null
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
          plugins        = Nil,
          tags           = Nil,
          operationId    = None
        )
      )

      // when
      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, targetSwagger, rules, ConverterConf(None, None)))
      println(io.swagger.util.Json.pretty(result))

      // then
      result.getBasePath mustBe "/api"

      result.getPaths.size() mustBe 1
      result.getPath("/apigw/users") must not be null
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
          plugins        = Nil,
          tags           = Nil,
          operationId    = None
        )
      )

      // when
      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, targetSwagger, rules, ConverterConf(None, None)))
      println(io.swagger.util.Json.pretty(result))

      // then
      result.getBasePath mustBe "/api"

      result.getPaths.size() mustBe 1
      result.getPath("/users") must not be null
      result.getPath("/users").getPut must not be null
    }

    "set host, basePath and scheme based on config" in {
      val getPath = sampleGetPath()
      val targetServiceSwagger = sampleSwagger("/", Map("/test" -> getPath))
      val apiGwGetPath = PathPattern("/test")
      val rules = List(
        OpenApiRule(
          method = HttpMethod.GET,
          serviceId = sampleServiceId,
          group = GroupMatchCriteria.empty,
          pathPattern = apiGwGetPath,
          pathPrefix = PathPrefix(""),
          dropPathPrefix = false,
          rewriteMethod = None,
          rewritePath = None,
          plugins = Nil,
          tags = Nil,
          operationId = None
        )
      )

      val conf = ConverterConf(
        defaults = Some(
          OpenApiDefaultsConf(
            host = Some(Host("example.com")),
            basePath = Some(BasePath("/api")),
            ssl = Some(true))),
        processors = None)
      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, targetServiceSwagger, rules, conf))

      println(io.swagger.util.Json.pretty(result))

      result.getHost mustBe "example.com"
      result.getBasePath mustBe "/api"
      result.getSchemes.asScala mustBe List(Scheme.HTTPS)

      result.getPaths.size() mustBe 1
      result.getPath("/test") mustBe getPath
    }

    "resolve operationId conflicts when rules proxy to the same api" in {
      val postPath = samplePostPath()
      val targetServiceSwagger = sampleSwagger("/", Map("/applications" -> postPath))

      val rules = List(
        OpenApiRule(
          method = HttpMethod.POST,
          serviceId = sampleServiceId,
          group = GroupMatchCriteria.empty,
          pathPattern = PathPattern("/applications"),
          pathPrefix = PathPrefix(""),
          dropPathPrefix = false,
          rewriteMethod = None,
          rewritePath = None,
          plugins = Nil,
          tags = Nil,
          operationId = None
        ),
        OpenApiRule(
          method = HttpMethod.POST,
          serviceId = sampleServiceId,
          group = GroupMatchCriteria.empty,
          pathPattern = PathPattern("/admin/applications"),
          pathPrefix = PathPrefix(""),
          dropPathPrefix = false,
          rewriteMethod = None,
          rewritePath = Some(RewritePath("/applications")),
          plugins = Nil,
          tags = Nil,
          operationId = None
        )
      )

      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, targetServiceSwagger, rules, ConverterConf(None, None)))
      println(io.swagger.util.Json.pretty(result))

      result.getPaths.size mustBe 2
      result.getPath("/applications").getPost.getOperationId mustBe "postApplications"
      result.getPath("/admin/applications").getPost.getOperationId mustBe "postAdminApplications"
    }

    "overwrite operationId from rule config so no conflicts when rules proxy to the same api" in {
      val postPath = samplePostPath()
      val targetServiceSwagger = sampleSwagger("/", Map("/applications" -> postPath))

      val rules = List(
        OpenApiRule(
          method = HttpMethod.POST,
          serviceId = sampleServiceId,
          group = GroupMatchCriteria.empty,
          pathPattern = PathPattern("/applications"),
          pathPrefix = PathPrefix(""),
          dropPathPrefix = false,
          rewriteMethod = None,
          rewritePath = None,
          plugins = Nil,
          tags = Nil,
          operationId = None
        ),
        OpenApiRule(
          method = HttpMethod.POST,
          serviceId = sampleServiceId,
          group = GroupMatchCriteria.empty,
          pathPattern = PathPattern("/admin/applications"),
          pathPrefix = PathPrefix(""),
          dropPathPrefix = false,
          rewriteMethod = None,
          rewritePath = Some(RewritePath("/applications")),
          plugins = Nil,
          tags = Nil,
          operationId = Some("operationId")
        )
      )

      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, targetServiceSwagger, rules, ConverterConf(None, None)))
      println(io.swagger.util.Json.pretty(result))

      result.getPaths.size mustBe 2
      result.getPath("/applications").getPost.getOperationId mustBe "postApi"
      result.getPath("/admin/applications").getPost.getOperationId mustBe "operationId"
    }

    "convert api when rule with drop prefix by adding prefix in generated swagger" in {
      val swagger = sampleSwagger("/", Map("/policy" -> samplePostPath()))

      val rules = List(
        OpenApiRule(
          method = HttpMethod.POST,
          serviceId = sampleServiceId,
          group = GroupMatchCriteria.empty,
          pathPattern = PathPattern("/policy"),
          pathPrefix = PathPrefix("/authz"),
          dropPathPrefix = true,
          rewriteMethod = None,
          rewritePath = None,
          plugins = Nil,
          tags = Nil,
          operationId = None
        ))
      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, swagger, rules, ConverterConf(None, None)))

      result.getBasePath mustBe "/api"
      result.getPaths.size() mustBe 1
      result.getPath("/authz/policy") must not be null
    }

    "convert api when rules with rewrite path proxying to generic api in target service" in {
      val swagger = sampleSwagger("/", Map("/relations/{relationType}/{relationValue}" -> samplePostPath()))

      val rules = List(
        OpenApiRule(
          method = HttpMethod.POST,
          serviceId = sampleServiceId,
          group = GroupMatchCriteria.empty,
          pathPattern = PathPattern("/device/trust"),
          pathPrefix = PathPrefix(""),
          dropPathPrefix = false,
          rewriteMethod = None,
          rewritePath = Some(RewritePath("/relations/trust/trusted")),
          plugins = Nil,
          tags = Nil,
          operationId = None
        )
      )
      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, swagger, rules, ConverterConf(None, None)))

      result.getBasePath mustBe "/api"
      result.getPaths.size() mustBe 1

      println(io.swagger.util.Json.pretty(result))
    }

    "convert api when rules with rewrite path and path params proxying to generic api in target service" in {
      val swagger = sampleSwagger("/", Map("/relations/device/{deviceUuid}/{relationType}" -> samplePostPath()))

      val rules = List(
        OpenApiRule(
          method = HttpMethod.POST,
          serviceId = sampleServiceId,
          group = GroupMatchCriteria.empty,
          pathPattern = PathPattern("/devices/{deviceUuid}/trust"),
          pathPrefix = PathPrefix(""),
          dropPathPrefix = false,
          rewriteMethod = None,
          rewritePath = Some(RewritePath("/relations/device/{deviceUuid}/trust")),
          plugins = Nil,
          tags = Nil,
          operationId = None
        )
      )
      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, swagger, rules, ConverterConf(None, None)))

      println(io.swagger.util.Json.pretty(result))

      result.getBasePath mustBe "/api"
      result.getPaths.size() mustBe 1

    }

    "convert rule with path variables mismatch" in {
      val targetServiceSwagger = sampleSwagger("/", Map("/application/{uuid}/capability/oauthClient" -> samplePostPath()))
      val apiGwGetPath = PathPattern("/application/{applicationId}/capability/oauthClient")

      val rules = List(
        OpenApiRule(
          method = HttpMethod.POST,
          serviceId = sampleServiceId,
          group = GroupMatchCriteria.empty,
          pathPattern = apiGwGetPath,
          pathPrefix = PathPrefix(""),
          dropPathPrefix = false,
          rewriteMethod = None,
          rewritePath = None,
          plugins = Nil,
          tags = Nil,
          operationId = None
        )
      )
      val result = await(converter.convert(TracingContext.dummy(), sampleServiceId, targetServiceSwagger, rules, ConverterConf(None, None)))

      println(io.swagger.util.Json.pretty(result))

      result.getBasePath mustBe "/api"
      result.getPaths.size() mustBe 1
    }

  }

}
