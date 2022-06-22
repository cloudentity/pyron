package com.cloudentity.pyron.acceptance

import com.cloudentity.pyron.apigroup.{ApiGroup, ApiGroupConf, ApiGroupsStore}
import com.cloudentity.pyron.domain.flow.{PluginName, ResponseCtx}
import com.cloudentity.pyron.plugin.config.ValidateResponse
import com.cloudentity.pyron.plugin.verticle.ResponsePluginVerticle
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.circe.Decoder
import io.restassured.RestAssured._
import io.vertx.core.{Future => VxFuture}
import io.vertx.ext.unit.TestContext
import org.junit.Test

import scala.concurrent.Future

class ApiHandlerGroupsAcceptanceTest extends ApiHandlerRulesAcceptanceTest {
  override def getMetaConfPath() = "src/test/resources/acceptance/api-handler/meta-config-api-groups.json"

  override def rulesTestBasePath: String = "/test-rules"

  @Test
  def shouldMatchBasePath(ctx: TestContext): Unit = {
    mockOnPath(targetService)("/should-match-base-path", resp().withStatusCode(200))

    given()
    .when()
      .get("/base-path/should-match-base-path")
    .`then`()
      .statusCode(200)
  }

  @Test
  def shouldDropBasePathWhenNotDroppingPathPrefix(ctx: TestContext): Unit = {
    mockOnPath(targetService)("/prefix/should-drop-base-path", resp().withStatusCode(200))

    given()
      .when()
    .get("/base-path/prefix/should-drop-base-path")
      .`then`()
      .statusCode(200)
  }

  @Test
  def shouldMatchDomainWhenHostWithoutPort(ctx: TestContext): Unit = {
    mockOnPath(targetService)("/should-match-domain-when-host-without-port", resp().withStatusCode(200))

    given()
      .header("Host", "example.com")
    .when()
      .get("/domain/should-match-domain-when-host-without-port")
    .`then`()
      .statusCode(200)
  }

  @Test
  def shouldMatchDomainWhenHostWithPort(ctx: TestContext): Unit = {
    mockOnPath(targetService)("/should-match-domain-when-host-with-port", resp().withStatusCode(200))

    given()
      .header("Host", "example.com:8080")
    .when()
      .get("/domain/should-match-domain-when-host-with-port")
    .`then`()
      .statusCode(200)
  }

  @Test
  def shouldMatchSubDomain(ctx: TestContext): Unit = {
    mockOnPath(targetService)("/should-match-sub-domain", resp().withStatusCode(200))

    given()
      .header("Host", "some.example.com")
    .when()
      .get("/subdomain/should-match-sub-domain")
    .`then`()
      .statusCode(200)
  }

  @Test
  def shouldMatchSubSubDomain(ctx: TestContext): Unit = {
    mockOnPath(targetService)("/should-match-sub-sub-domain", resp().withStatusCode(200))

    given()
      .header("Host", "x.some.example.com")
    .when()
      .get("/subsubdomain/should-match-sub-sub-domain")
    .`then`()
      .statusCode(200)
  }

  @Test
  def shouldNotMatchSubSubDomain(ctx: TestContext): Unit = {
    mockOnPath(targetService)("/should-not-match-sub-domain", resp().withStatusCode(200))

    given()
      .header("Host", "invalid.x.some.example.com")
    .when()
      .get("/subsubdomain/should-not-match-sub-domain")
    .`then`()
      .statusCode(404)
  }

  @Test
  def shouldNotMatchIfBasePathMatchesAndDomainDoesNot(ctx: TestContext): Unit = {
    mockOnPath(targetService)("/should-not-match-if-base-path-matches-and-domain-does-not", resp().withStatusCode(200))

    given()
      .header("Host", "invalid.example.com")
    .when()
      .get("/base-and-domain/should-not-match-if-base-path-matches-and-domain-does-not")
    .`then`()
      .statusCode(404)
  }

  @Test
  def shouldNotMatchIfDomainMatchesAndBasePathDoesNot(ctx: TestContext): Unit = {
    mockOnPath(targetService)("/path", resp().withStatusCode(200))

    given()
      .header("Host", "example.com")
    .when()
      .get("/invalid/should-not-match-if-domain-matches-and-base-path-does-not")
    .`then`()
      .statusCode(404)
  }

  @Test
  def shouldUseApiGroupPlugin(ctx: TestContext): Unit = {
    mockOnPath(targetService)("/should-set-response-header", resp().withStatusCode(200))

    // we make the call twice to detect plugins binding to the same bus address
    given()
    .when()
      .get("/plugins/a/should-set-response-header")
    .`then`()
      .statusCode(200)
      .header("X-A", "a")

    given()
      .when()
      .get("/plugins/a/should-set-response-header")
      .`then`()
      .statusCode(200)
      .header("X-A", "a")

    given()
      .when()
      .get("/plugins/b/should-set-response-header")
      .`then`()
      .statusCode(200)
      .header("X-B", "b")

    given()
    .when()
      .get("/plugins/b/should-set-response-header")
    .`then`()
      .statusCode(200)
      .header("X-B", "b")
  }

  @Test
  def shouldOverwriteApiGroupPlugin(ctx: TestContext): Unit = {
    mockOnPath(targetService)("/should-overwrite-api-group-plugin", resp().withStatusCode(200))

    // we make the call twice to detect plugins binding to the same bus address
    given()
    .when()
      .get("/plugins/overwrite/a/should-overwrite-api-group-plugin")
    .`then`()
      .statusCode(200)
      .header("X-A", "a")

    given()
    .when()
      .get("/plugins/overwrite/b/should-overwrite-api-group-plugin")
    .`then`()
      .statusCode(200)
      .header("X-B", "b")
  }

  @Test
  override def shouldApplyReroute(ctx: TestContext): Unit = {
    // disabled test because reroute does not copy api-group's basePath
  }

  @Test
  override def shouldBreakRerouteLoop(ctx: TestContext): Unit = {
    // disabled test because reroute does not copy api-group's basePath
  }
}

class TestApiGroupsStore(apiGroups: List[ApiGroup]) extends ScalaServiceVerticle with ApiGroupsStore {
  override def getGroups(): VxFuture[List[ApiGroup]] = VxFuture.succeededFuture(apiGroups)
  override def getGroupConfs(): VxFuture[List[ApiGroupConf]] = VxFuture.succeededFuture(Nil)
}

class ResponseHeaderPlugin extends ResponsePluginVerticle[Unit] {
  override def name: PluginName = PluginName("responseHeader")

  override def apply(responseCtx: ResponseCtx, conf: Unit): Future[ResponseCtx] = Future.successful {
    responseCtx.modifyResponse(_.modifyHeaders(_.setHeaders(
      Map(getConfig.getString("headerName") -> getConfig.getString("headerValue"))
    )))
  }

  override def validate(conf: Unit): ValidateResponse = ValidateResponse.ok()

  override def confDecoder: Decoder[Unit] = Decoder.decodeUnit
}