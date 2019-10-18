package com.cloudentity.pyron.acceptance

import com.cloudentity.pyron.apigroup.{ApiGroup, ApiGroupConf, ApiGroupsStore}
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.restassured.RestAssured._
import io.vertx.core.{Future => VxFuture}
import io.vertx.ext.unit.TestContext
import org.junit.Test

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
}

class TestApiGroupsStore(apiGroups: List[ApiGroup]) extends ScalaServiceVerticle with ApiGroupsStore {
  override def getGroups(): VxFuture[List[ApiGroup]] = VxFuture.succeededFuture(apiGroups)
  override def getGroupConfs(): VxFuture[List[ApiGroupConf]] = VxFuture.succeededFuture(Nil)
}