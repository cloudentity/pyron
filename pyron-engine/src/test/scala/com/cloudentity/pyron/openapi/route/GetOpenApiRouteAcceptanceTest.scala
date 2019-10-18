package com.cloudentity.pyron.admin.route.openapi

import com.fasterxml.jackson.databind.ObjectWriter
import com.cloudentity.pyron.PyronAcceptanceTest
import com.cloudentity.pyron.domain.openapi.OpenApiRule
import com.cloudentity.pyron.util.MockUtils
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.config.EncoderConfig
import io.swagger.models._
import io.swagger.parser.SwaggerParser
import org.hamcrest.core.Is
import org.hamcrest.{BaseMatcher, Description}
import org.junit._
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpResponse

import scala.collection.JavaConverters._

object GetOpenApiRouteAcceptanceTest {
  var expectedRules: List[OpenApiRule] = _
  var expectedOpenApi: Swagger =
    new Swagger().info(new Info().version("1.0.0").title("Service"))

  val path = new Path()
  path.setGet(new Operation().operationId("operation").response(200, new Response().description("ok")))

  expectedOpenApi.setBasePath("/api")
  expectedOpenApi.setHost("localhost")
  expectedOpenApi.setSchemes(List(Scheme.HTTPS).asJava)
  expectedOpenApi.setPaths(Map("/path" -> path).asJava)
  expectedOpenApi.setSwagger("2.0")

}

class OpenApiMatcher(val expectedOpenApi: Swagger, val writer: ObjectWriter) extends BaseMatcher[String]{
  val parser: SwaggerParser = new SwaggerParser()
  var expected: String = _

  override def matches(item: scala.Any): Boolean = {
    val actualSwagger: Swagger = parser.parse(item.asInstanceOf[String])
    expected = writer.writeValueAsString(expectedOpenApi)
    expected == writer.writeValueAsString(actualSwagger)
  }

  override def describeTo(description: Description): Unit = description.appendText(expected)
}

class GetOpenApiRouteAcceptanceTest extends PyronAcceptanceTest with MockUtils {
  override def getMetaConfPath() = "src/test/resources/openapi/meta-config.json"

  var targetServiceA: ClientAndServer = _
  var targetServiceB: ClientAndServer = _
  var targetServiceC: ClientAndServer = _

  @Before
  def init(): Unit = {
    targetServiceA = startClientAndServer(7760)
    targetServiceB = startClientAndServer(7770)
    targetServiceC = startClientAndServer(7780)
    Thread.sleep(200)
  }

  @After
  def clean(): Unit = {
    targetServiceA.close()
    targetServiceB.close()
    targetServiceC.close()
  }

  val defaultOpenApi =
    """
      |{
      |  "swagger": "2.0",
      |  "info": {
      |    "version": "1.0.0",
      |    "title": "Service"
      |  },
      |  "paths": {
      |    "/path": {
      |      "get": {
      |        "operationId": "operation",
      |        "responses": {
      |          "200": {
      |            "description": "ok"
      |          }
      |        }
      |      }
      |    }
      |  }
      |}
    """.stripMargin


  @Test
  def shouldConvertForDiscoverableService(): Unit = {
    mockOnPath(targetServiceA)("/docs/openapi.json", HttpResponse.response(defaultOpenApi).withStatusCode(200))

    val jsonOpenApiMatcher = new OpenApiMatcher(GetOpenApiRouteAcceptanceTest.expectedOpenApi, io.swagger.util.Json.pretty())

    given()
    .when()
      .get(s"/openapi/service-a")
    .`then`()
      .statusCode(200)
      .body(jsonOpenApiMatcher)
  }

  @Test
  def shouldProduceApiDocsInYamlFormat(): Unit = {
    mockOnPath(targetServiceA)("/docs/openapi.json", HttpResponse.response(defaultOpenApi).withStatusCode(200))

    val contentType = "application/x-yaml"

    val yamlOpenApiMatcher = new OpenApiMatcher(GetOpenApiRouteAcceptanceTest.expectedOpenApi, io.swagger.util.Yaml.pretty())

    given()
      .contentType(contentType)
      .config(RestAssured.config().encoderConfig(EncoderConfig.encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
    .when()
      .get(s"/openapi/service-a")
    .`then`()
      .contentType(contentType)
      .statusCode(200)
      .body(yamlOpenApiMatcher)
  }

  @Test
  def shouldUseDefaultOpenApiUriIfMissing(): Unit = {
    mockOnPath(targetServiceB)("/docs/openapi", HttpResponse.response(defaultOpenApi).withStatusCode(200))

    given()
    .when()
      .get(s"/openapi/service-b")
    .`then`()
      .statusCode(200)
  }

  @Test
  def shouldConvertForStaticService(): Unit = {
    mockOnPath(targetServiceC)("/docs/openapi", HttpResponse.response(defaultOpenApi).withStatusCode(200))

    given()
    .when()
      .get(s"/openapi/localhost?port=7780")
    .`then`()
      .statusCode(200)
  }

  @Test
  def shouldReturn400IfInvalidServiceId(): Unit = {
    given()
    .when()
      .get(s"/openapi/x?port=y")
    .`then`()
      .statusCode(400)
  }

  @Test
  def shouldReturn404IfServiceIdDoesntMatchAnyRules(): Unit = {
    given()
    .when()
      .get(s"/openapi/missing-service")
    .`then`()
      .statusCode(404)
      .body("code", Is.is("OpenApiNotFound"))
  }

  @Test
  def shouldReturn404IfServiceDoesNotExposeOpenApiDocs(): Unit = {
    given()
    .when()
      .get(s"/openapi/service-a")
    .`then`()
      .statusCode(404)
      .body("code", Is.is("OpenApiNotFound"))
  }

  @Test
  def shouldReturn404IfServiceIsExcluded(): Unit = {
    given()
    .when()
      .get(s"/openapi/virtual")
    .`then`()
      .statusCode(404)
      .body("code", Is.is("OpenApiNotFound"))
  }

}