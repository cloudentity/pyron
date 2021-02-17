package com.cloudentity.pyron.openapi.route

import io.circe.Printer
import io.circe.syntax._
import com.cloudentity.pyron.PyronAcceptanceTest
import com.cloudentity.pyron.domain.flow.ServiceClientName
import com.cloudentity.pyron.domain.openapi.DiscoverableServiceId
import com.cloudentity.pyron.openapi.route.ListOpenApiRoute.{ListOpenApiResponse, ServiceMetadata, Url}
import com.cloudentity.pyron.util.MockUtils
import io.restassured.RestAssured.given
import org.hamcrest.{BaseMatcher, Description}
import org.junit.Assert.assertEquals
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpResponse
import org.scalatest.MustMatchers


class ListOpenApiRouteAcceptanceTest extends PyronAcceptanceTest with MustMatchers with MockUtils {
  override def getMetaConfPath = "src/test/resources/openapi/meta-config.json"

  val prettyPrinter: Printer = Printer.noSpaces.copy(dropNullValues = true)

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

  val defaultOpenApi: String =
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
  def listOpenApi(): Unit = {
    mockOnPath(targetServiceA)("/docs/openapi.json", HttpResponse.response(defaultOpenApi).withStatusCode(200))

    val expected = ListOpenApiResponse(
      Map(
        DiscoverableServiceId(ServiceClientName("localhost?port=7780&ssl=false")) -> ServiceMetadata.notAvailable(),
        DiscoverableServiceId(ServiceClientName("service-b")) -> ServiceMetadata.notAvailable(),
        DiscoverableServiceId(ServiceClientName("service-a")) -> ServiceMetadata.withUrl(Url("http://localhost/openapi/service-a"))
      )
    )

    val matcher = new ListOpenApiMatcher(expected, prettyPrinter)

    given()
      .when()
        .get(s"/openapi")
      .`then`()
        .statusCode(200)
        .body(matcher)
  }

  class ListOpenApiMatcher(val expected: ListOpenApiResponse, val printer: Printer) extends BaseMatcher[ListOpenApiResponse] {
    val expectedJson: String = expected.asJson.pretty(printer)

    override def matches(response: scala.Any): Boolean = {
      assertEquals(expectedJson, response.asInstanceOf[String])
      true
    }

    override def describeTo(description: Description): Unit = description.appendText(expectedJson)
  }

}
