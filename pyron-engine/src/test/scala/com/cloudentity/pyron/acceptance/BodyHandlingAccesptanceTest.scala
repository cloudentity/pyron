package com.cloudentity.pyron.acceptance

import java.io.{ByteArrayInputStream, File}
import java.nio.charset.Charset

import com.cloudentity.pyron.PyronAcceptanceTest
import com.cloudentity.pyron.util.MockUtils
import io.restassured.RestAssured.`given`
import io.restassured.builder.RequestSpecBuilder
import io.restassured.config.{ConnectionConfig, EncoderConfig, HttpClientConfig, MultiPartConfig, RestAssuredConfig}
import io.restassured.http.ContentType
import io.vertx.core.http.HttpClientOptions
import io.vertx.ext.unit.TestContext
import org.apache.http.client.HttpClient
import org.apache.http.impl.client.DefaultHttpClient
import org.junit.{After, Assert, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response

class BodyHandlingAccesptanceTestextends extends PyronAcceptanceTest with MockUtils {
  override def getMetaConfPath() = "src/test/resources/acceptance/body-handling/meta-config.json"

  var targetService: ClientAndServer = null
  val body1024Bytes = List.fill(102)("abcdefghij").mkString("") + "abcd"
  val body1025Bytes = body1024Bytes + "e"

  @Before
  def before(): Unit = {
    targetService = ClientAndServer.startClientAndServer(7760)
  }

  @After
  def finish(): Unit = {
    targetService.stop
  }

  @Test
  def testBufferWithContentLength(): Unit = {
    targetService
      .when(request().withMethod("POST").withPath("/upload/buffer/limit"))
      .respond(response.withStatusCode(200))

    given()
      .body(body1024Bytes)
    .when()
      .post("/upload/buffer/limit")
    .`then`()
      .statusCode(200)

    given()
      .body(body1025Bytes)
    .when()
      .post("/upload/buffer/limit")
    .`then`()
      .statusCode(413)
  }

  @Test
  def testStreamWithContentLength(): Unit = {
    targetService
      .when(request().withMethod("POST").withPath("/upload/stream/limit"))
      .respond(response.withStatusCode(200))

    given()
      .body(body1024Bytes)
    .when()
      .post("/upload/stream/limit")
    .`then`()
      .statusCode(200)

    given()
      .body(body1025Bytes)
    .when()
      .post("/upload/stream/limit")
    .`then`()
      .statusCode(413)
  }

  @Test
  def testBufferWithTransferChunkedWithinLimits(ctx: TestContext): Unit = {
    val async = ctx.async()
    targetService
      .when(request().withMethod("POST").withPath("/upload/buffer/limit"))
      .respond(response.withStatusCode(200))

    vertx.createHttpClient(new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8080))
      .post("/upload/buffer/limit")
      .setChunked(true)
      .handler { resp =>
        ctx.assertEquals(200, resp.statusCode())
        async.complete()
      }.exceptionHandler{ ex =>
        ctx.fail(ex)
        async.complete()
      }.end(body1024Bytes)
  }

  @Test
  def testBufferWithTransferChunkedLimitsReached(ctx: TestContext): Unit = {
    val async = ctx.async()
    targetService
      .when(request().withMethod("POST").withPath("/upload/buffer/limit"))
      .respond(response.withStatusCode(200))

    vertx.createHttpClient(new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8080))
      .post("/upload/buffer/limit")
      .setChunked(true)
      .handler { resp =>
        ctx.assertEquals(413, resp.statusCode())
        async.complete()
      }.exceptionHandler{ ex =>
      ctx.fail(ex)
      async.complete()
    }.end(body1025Bytes)
  }

  @Test
  def testStreamWithTransferChunkedWithinLimits(ctx: TestContext): Unit = {
    val async = ctx.async()
    targetService
      .when(request().withMethod("POST").withPath("/upload/stream/limit"))
      .respond(response.withStatusCode(200))

    vertx.createHttpClient(new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8080))
      .post("/upload/stream/limit")
      .setChunked(true)
      .handler { resp =>
        ctx.assertEquals(200, resp.statusCode())
        async.complete()
      }.exceptionHandler{ ex =>
      ctx.fail(ex)
      async.complete()
    }.end(body1024Bytes)
  }

  @Test
  def testStreamWithTransferChunkedLimitsReached(ctx: TestContext): Unit = {
    val async = ctx.async()
    targetService
      .when(request().withMethod("POST").withPath("/upload/stream/limit"))
      .respond(response.withStatusCode(200))

    vertx.createHttpClient(new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8080))
      .post("/upload/stream/limit")
      .setChunked(true)
      .handler { resp =>
        ctx.assertEquals(413, resp.statusCode())
        async.complete()
      }.exceptionHandler{ ex =>
      ctx.fail(ex)
      async.complete()
    }.end(body1025Bytes)
  }
}
