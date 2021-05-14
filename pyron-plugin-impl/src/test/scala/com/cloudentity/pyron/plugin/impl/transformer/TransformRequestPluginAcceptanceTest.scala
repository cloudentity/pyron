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
  override def getMetaConfPath: String = "src/test/resources/plugins/transformer/meta-config.json"

  var targetService: ClientAndServer = _

  def getHeaderAllValues(req: HttpRequest, headerName: String): Option[List[String]] =
    req.getHeaders.asScala.toList.find(_.getName.toString == headerName)
      .map(v => v.getValues.asScala.toList.map(_.toString))

  def getHeaderOnlyValue(req: HttpRequest, headerName: String): Option[String] = {
    getHeaderAllValues(req, headerName).map { values =>
      assert(values.size == 1)
      values.head
    }
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
  def shouldSetPathParamFromQueryParam(): Unit = {
    given()
      .queryParam("userUuid", "123")
      .when()
      .get("/path-param-from-query-param/value")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req =>
      req.getPath mustBe "/path-param-from-query-param/123"
    }
  }

  @Test
  def shouldSetPathParamFromCookie(): Unit = {
    given()
      .cookie("userUuid", "123")
      .when()
      .get("/path-param-from-cookie/value")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req =>
      req.getPath mustBe "/path-param-from-cookie/123"
    }
  }

  @Test
  def shouldSetFixedQueryParam(): Unit = {
    given()
      .when()
      .get("/fixed-query-param")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req =>
      req.getFirstQueryStringParameter("userUuid") mustBe "123"
      req.getPath mustBe "/fixed-query-param"
    }
  }

  @Test
  def shouldSetQueryParamFromHeader(): Unit = {
    given()
      .header("userUuid", "123")
      .when()
      .get("/query-param-from-header")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req =>
      req.getFirstQueryStringParameter("userUuid") mustBe "123"
      req.getPath mustBe "/query-param-from-header"
    }
  }

  @Test
  def shouldSetQueryParamFromPathParam(): Unit = {
    given()
      .when()
      .get("/query-param-from-path-param/123")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req =>
      req.getFirstQueryStringParameter("userUuid") mustBe "123"
      req.getPath mustBe "/query-param-from-path-param"
    }
  }

  @Test
  def shouldSetQueryParamFromCookie(): Unit = {
    given()
      .cookie("userUuid", "123")
      .when()
      .get("/query-param-from-cookie")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req =>
      req.getFirstQueryStringParameter("userUuid") mustBe "123"
      req.getPath mustBe "/query-param-from-cookie"
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
  def shouldSetBodyAttributeFromQueryParam(): Unit = {
    given()
      .body("""{"attr":"x"}""")
      .queryParam("param", "v1", "v2", "v3")
      .when()
      .post("/body-from-query-param")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req =>
      req.getBodyAsString mustBe """{"attr":["v1","v2","v3"]}"""
    }
  }


  @Test
  def shouldSetBodyAttributeFromCookie(): Unit = {
    given()
      .cookie("userUuid", "123")
      .body("""{"attr":"x"}""")
      .when()
      .post("/body-from-cookie")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req =>
      req.getBodyAsString mustBe """{"attr":"123"}"""
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
  def shouldSetHeaderFromQueryParam(): Unit = {
    given()
      .when()
      .queryParam("param", "value")
      .get("/header-from-query-param")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req => getHeaderOnlyValue(req, "H") mustBe Some("value") }
  }

  @Test
  def shouldSetHeaderFromCookie(): Unit = {
    given()
      .when()
      .cookie("param", "value")
      .get("/header-from-cookie")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req => getHeaderOnlyValue(req, "H") mustBe Some("value") }
  }

  @Test
  def shouldSetHeaderFromBody(): Unit = {
    given()
      .body("""{"h": "value"}""")
      .when()
      .post("/header-from-body")
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
  def shouldSetHeaderFromDynamicScopeUsingAllOfTheValuesWhichMatchThePattern(): Unit = {
    val rand = scala.util.Random
    val envOneId = rand.nextInt.abs
    val envTwoId = rand.nextInt.abs

    given()
      .body(
        s"""{"scp": ["unrelated-value", "env.$envOneId", "another-value", "env.$envTwoId"],"groups": "admin"}""")
      .when()
      .get("/dyn-header-will-obtain-all-the-values-matching-the-pattern")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req => getHeaderAllValues(req, "X-Env") mustBe Some(List(s"$envOneId", s"$envTwoId")) }
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
  def shouldSetHeaderFromDynamicScopeAndAllowDefiningPatternParametersWhichSpecifySizeOfStringToBeCaptured(): Unit = {

    given()
      .body(
        s"""{"scp": [
           |"account-X12-34-56-78",
           |"account-USA1234-3456-5678-7890",
           |"stuff",
           |"account-UK7890-5678-3456-1234",
           |"account-3456-1234-7890-5678"
           |],"groups": "admin"}""".stripMargin)
      .when()
      .get("/dyn-header-can-use-pattern-parameters-with-defined-size")
      .`then`()
      .statusCode(200)

    assertTargetRequest { req => getHeaderAllValues(req, "X-Account") mustBe Some(
      List("USA:1234345656787890", "UK:7890567834561234", ":3456123478905678"))
    }
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
    val envOneId = rand.nextInt.abs
    val customerId = rand.nextInt.abs
    val swiftId = rand.nextInt.abs
    val envTwoId = rand.nextInt.abs
    val paymentId = rand.nextInt.abs
    val transferId = rand.nextInt.abs

    given()
      .body(
        s"""{
           |"scp": [
           |  "env.($envOneId)",
           |  "customer-{$customerId}_swift_$swiftId",
           |  "env.($envTwoId)",
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
    assertTargetRequest { req => getHeaderAllValues(req, "X-Env") mustBe Some(List(s"$envOneId", s"$envTwoId")) }
  }

  def assertTargetRequest(f: HttpRequest => Unit): Unit = {
    targetService.retrieveRecordedRequests(null).length mustBe 1
    f(targetService.retrieveRecordedRequests(null)(0))
  }
}
