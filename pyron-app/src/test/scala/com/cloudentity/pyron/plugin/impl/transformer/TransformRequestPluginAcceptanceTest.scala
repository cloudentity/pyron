package com.cloudentity.pyron.plugin.impl.transformer

import com.cloudentity.pyron.plugin.impl.PluginAcceptanceTest
import io.restassured.RestAssured.given
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.scalatest.MustMatchers

import scala.collection.JavaConverters._

class TransformRequestPluginAcceptanceTest extends PluginAcceptanceTest with MustMatchers {
  override def getMetaConfPath(): String = "src/test/resources/plugins/transformer/meta-config.json"

  var targetService: ClientAndServer = _

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
    targetService.when(new HttpRequest()).respond(new HttpResponse().withStatusCode(200))
  }

  @After
  def finish(): Unit = {
    targetService.stop
  }

  @Test
  def shouldSetFixedPathParam(): Unit = {
    given()
    .when()
      .get("/fixed-path-param/value")
    .`then`()
      .statusCode(200)

    assertTargetRequest { req =>
      req.getPath must be ("/fixed-path-param/fixed-param")
    }
  }

  @Test
  def shouldSetPathParamFromHeader(): Unit = {
    given()
      .header("userUuid", "123")
    .when()
      .get("/path-param-from-header/value")
    .`then`()
      .statusCode(200)

    assertTargetRequest { req =>
      req.getPath must be ("/path-param-from-header/123")
    }
  }

  @Test
  def shouldSetFixedBodyAttribute(): Unit = {
    given()
      .body("""{"attr":"x"}""")
    .when()
      .post("/fixed-body")
    .`then`()
      .statusCode(200)

    assertTargetRequest { req =>
      req.getBodyAsString must be ("""{"attr":"value"}""")
    }
  }

  @Test
  def shouldSetBodyAttributeFromPathParam(): Unit = {
    given()
      .body("""{"attr":"x"}""")
    .when()
      .post("/body-from-path-param/value")
    .`then`()
      .statusCode(200)

    assertTargetRequest { req =>
      req.getBodyAsString must be ("""{"attr":"value"}""")
    }
  }

  @Test
  def shouldDropBody(): Unit = {
    given()
      .body("""{"attr":"x"}""")
    .when()
      .post("/body-dropped")
    .`then`()
      .statusCode(200)

    assertTargetRequest { req =>
      req.getBodyAsRawBytes.length must be (0)
    }
  }

  @Test
  def shouldSetFixedHeader(): Unit = {
    given()
    .when()
      .get("/fixed-header")
    .`then`()
      .statusCode(200)

    assertTargetRequest { req =>
      req.getHeaders.asScala.toList.find(_.getName == "H").map(_.getValues.get(0)) must be (Some("value"))
    }
  }

  @Test
  def shouldSetHeaderFromPathParam(): Unit = {
    given()
    .when()
      .get("/header-from-path-param/value")
    .`then`()
      .statusCode(200)

    assertTargetRequest { req =>
      req.getHeaders.asScala.toList.find(_.getName == "H").map(_.getValues.get(0)) must be (Some("value"))
    }
  }

  @Test
  def shouldSetHeaderFromBody(): Unit = {
    given()
      .body("""{"h": "value"}""")
    .when()
      .get("/header-from-body")
    .`then`()
      .statusCode(200)

    assertTargetRequest { req =>
      req.getHeaders.asScala.toList.find(_.getName == "H").map(_.getValues.get(0)) must be (Some("value"))
    }
  }

  def assertTargetRequest(f: HttpRequest => Unit): Unit = {
    targetService.retrieveRecordedRequests(null).size must be(1)
    f(targetService.retrieveRecordedRequests(null)(0))
  }
}
