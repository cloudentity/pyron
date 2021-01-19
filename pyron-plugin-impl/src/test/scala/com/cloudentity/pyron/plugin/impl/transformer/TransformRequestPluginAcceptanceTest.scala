package com.cloudentity.pyron.plugin.impl.transformer

import com.cloudentity.pyron.plugin.impl.PluginAcceptanceTest
import io.restassured.RestAssured.given
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse, NottableString}
import org.scalatest.MustMatchers

import scala.collection.JavaConverters._

class TransformRequestPluginAcceptanceTest extends PluginAcceptanceTest with MustMatchers {
  override def getMetaConfPath: String = "src/test/resources/plugins/transformer/meta-config.json"

  var targetService: ClientAndServer = _

  def getHeaderOnlyValue(req: HttpRequest, headerName: String): Option[NottableString] =
    req.getHeaders.asScala.toList.find(_.getName.toString == headerName).map { v =>
      assert(v.getValues.size() == 1)
      v.getValues.get(0)
    }

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
      req.getPath mustBe "/fixed-path-param/fixed-param"
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
      req.getPath mustBe "/path-param-from-header/123"
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
      req.getBodyAsString mustBe """{"attr":"value"}"""
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
      req.getBodyAsString mustBe """{"attr":"value"}"""
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
      req.getBodyAsRawBytes.length mustBe 0
    }
  }

  @Test
  def shouldSetFixedHeader(): Unit = {
    given()
    .when()
      .get("/fixed-header")
    .`then`()
      .statusCode(200)

    assertTargetRequest { req => getHeaderOnlyValue(req, "H") mustBe Some("value") }
  }

  @Test
  def shouldSetHeaderFromPathParam(): Unit = {
    given()
    .when()
      .get("/header-from-path-param/value")
    .`then`()
      .statusCode(200)

    assertTargetRequest { req => getHeaderOnlyValue(req, "H") mustBe Some("value") }
  }

  @Test
  def shouldSetHeaderFromBody(): Unit = {
    given()
      .body("""{"h": "value"}""")
    .when()
      .get("/header-from-body")
    .`then`()
      .statusCode(200)

    assertTargetRequest { req => getHeaderOnlyValue(req, "H") mustBe Some("value") }
  }

  @Test
  def shouldSetHeaderFromDynamicScopeAndFindMultipleParamsInPatternAndReorderThemInOutput(): Unit = {
    val rand = scala.util.Random
    val transactionId = rand.nextInt.abs
    val swiftId = rand.nextInt.abs

    given()
      .body(
        s"""{"scp": ["unrelated-value-123", "transaction.$transactionId/swift.$swiftId"],"groups": "admin"}""")
      .when()
      .get("/dyn-header-can-find-multiple-params-in-pattern-and-reorder-them-in-input")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req => getHeaderOnlyValue(req, "X-Transaction") mustBe Some(s"$swiftId.$transactionId") }
  }

  @Test
  def shouldSetHeaderWhenPatternMatchesEntireValueButNotWhenItMatchesPartOfAWiderValue(): Unit = {
    val rand = scala.util.Random
    val paymentId = rand.nextInt.abs
    val transactionId = rand.nextInt.abs
    val envIdNoSuffix = rand.nextInt.abs
    val envIdSuffixed = rand.nextInt.abs

    given()
      .body(
        s"""{"scp": [
           |"unrelated-value-123",
           |"transaction.$transactionId",
           |"env.$envIdNoSuffix",
           |"env.$envIdSuffixed.suffix",
           |"non-payment.$paymentId"
           |],"groups": "admin"}""".stripMargin)
      .when()
      .get("/dyn-header-requires-pattern-matching-on-entire-value")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req => getHeaderOnlyValue(req, "X-Transaction") mustBe Some(s"$transactionId") }
    assertTargetRequest { req => getHeaderOnlyValue(req, "X-Payment") mustBe None }
    assertTargetRequest { req => getHeaderOnlyValue(req, "X-Env") mustBe Some(s"$envIdSuffixed") }
  }

  @Test
  def shouldSetHeaderFromDynamicScopeAndMatchAnyFirstValueForTakeAllPattern(): Unit = {
    given()
      .body(
        s"""{"scp": ["first-value", "another-value"],"groups": "admin"}""")
      .when()
      .get("/dyn-header-will-get-first-value-for-take-all-pattern")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req => getHeaderOnlyValue(req, "X-Value") mustBe Some("first-value") }
  }

  @Test
  def shouldSetHeaderFromDynamicScopeAndMatchRegexSpecialCharsLikeDotOrParensLiterally(): Unit = {
    val rand = scala.util.Random
    val paymentId = rand.nextInt.abs

    given()
      .body(
        s"""{"scp": ["unrelated-value-123", "(payment).is$$ok[$paymentId]?"],"groups": "admin"}""")
      .when()
      .get("/dyn-header-can-match-regex-special-chars-literally")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req => getHeaderOnlyValue(req, "X-Payment") mustBe Some(s"$paymentId") }
  }

  @Test
  def shouldSetHeaderFromDynamicScopeAndMatchLiteralCurlyBracesFromPatternWithDoubledCurlyBraces(): Unit = {
    val rand = scala.util.Random
    val customerId = rand.nextInt.abs

    given()
      .body(
        s"""{"scp": ["unrelated-value-123", "customer-{$customerId}"],"groups": "admin"}""")
      .when()
      .get("/dyn-header-can-match-literal-curly-braces")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req => getHeaderOnlyValue(req, "X-Client") mustBe Some(s"$customerId") }
  }

  @Test
  def shouldSetHeaderFromDynamicScopeAndAllowFixedMappingWhenPointedToNonArrayValue(): Unit = {

    given()
      .body(
        s"""{"scp": ["unrelated-value-123", "stuff"],"groups": "admin"}""")
      .when()
      .get("/dyn-header-can-use-fixed-mapping-for-non-array-values")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req => getHeaderOnlyValue(req, "X-DSKey") mustBe Some("elevated") }
  }

  @Test
  def shouldSetHeaderFromDynamicScopeAndAllowDynamicMappingWhenPointedToNonArrayValue(): Unit = {
    val rand = scala.util.Random
    val envId = rand.nextInt.abs

    given()
      .body(
        s"""{"scp": ["unrelated-value-123", "stuff"],"env": "env.$envId"}""")
      .when()
      .get("/dyn-header-can-use-dyn-mapping-for-non-array-values")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req => getHeaderOnlyValue(req, "X-Env") mustBe Some(s"$envId") }
  }

  @Test
  def shouldSetHeaderFromDynamicScopeAndApplyMultipleTransformationsAtOnce(): Unit = {
    val rand = scala.util.Random
    val paymentId = rand.nextInt.abs
    val transferId = rand.nextInt.abs
    val customerId = rand.nextInt.abs
    val swiftId = rand.nextInt.abs
    val envId = rand.nextInt.abs

    given()
      .body(
        s"""{
           |"scp": [
           |  "env.($envId)",
           |  "customer-{$customerId}_swift_$swiftId",
           |  "payment.[$paymentId]",
           |  "transfer.$transferId"
           |],
           |"groups": "admin"
           |}""".stripMargin)
      .when()
      .get("/dyn-header-with-multiple-transformations")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req => getHeaderOnlyValue(req, "X-Scope") mustBe Some("elevated") }
    assertTargetRequest { req => getHeaderOnlyValue(req, "X-Client") mustBe Some(s"$swiftId.$customerId") }
    assertTargetRequest { req => getHeaderOnlyValue(req, "X-SCP-Payment") mustBe Some(s"$paymentId") }
    assertTargetRequest { req => getHeaderOnlyValue(req, "X-SCP-Transfer") mustBe Some(s"$transferId") }
    assertTargetRequest { req => getHeaderOnlyValue(req, "DSKey") mustBe Some(s"$envId") }
  }

  def assertTargetRequest(f: HttpRequest => Unit): Unit = {
    targetService.retrieveRecordedRequests(null).length mustBe 1
    f(targetService.retrieveRecordedRequests(null)(0))
  }
}
