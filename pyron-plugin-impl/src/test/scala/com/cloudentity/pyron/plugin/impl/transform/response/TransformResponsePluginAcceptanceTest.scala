package com.cloudentity.pyron.plugin.impl.transform.response

import com.cloudentity.pyron.plugin.impl.PluginAcceptanceTest
import io.restassured.RestAssured.`given`
import org.hamcrest.core.IsEqual.equalTo
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.scalatest.MustMatchers

import scala.collection.JavaConverters._
import scala.language.implicitConversions
import scala.util.Random

class TransformResponsePluginAcceptanceTest extends PluginAcceptanceTest with MustMatchers {

  def asJavaArray[A](l: List[A]): java.util.ArrayList[A] = new java.util.ArrayList[A](l.asJava)

  var targetService: ClientAndServer = _

  override def getMetaConfPath: String = "src/test/resources/plugins/transform-response/meta-config.json"

  val rand: Random.type = scala.util.Random
  val transactionId: Int = rand.nextInt.abs
  val paymentId = rand.nextInt.abs
  val swiftId: Int = rand.nextInt.abs
  val envIdNoSuffix: Int = rand.nextInt.abs
  val envIdSuffixed: Int = rand.nextInt.abs
  val idOne: Int = rand.nextInt.abs
  val idTwo: Int = rand.nextInt.abs

  @Before
  def before(): Unit = {

    targetService = startClientAndServer(7760)
    targetService.when(new HttpRequest()).respond(new HttpResponse()
      .withHeader("Content-Type", "application/json")
      .withHeader("userUuid", "12345")
      .withBody(
        s"""{
           |"h": "bodyParamValue",
           |"groups": "admin",
           |"scp": [
           |  "id.$idOne",
           |  "id.$idTwo",
           |  "unrelated-value-123",
           |  "transaction.$transactionId/swift.$swiftId",
           |  "env.$envIdNoSuffix",
           |  "env.$envIdSuffixed.suffix",
           |  "non-payment.$paymentId"
           |]
           |}""".stripMargin
      ).withStatusCode(200)
    )
  }

  @After
  def finish(): Unit = {
    targetService.stop
  }

  @Test
  def shouldSetBodyAttributeFromPathParam(): Unit = {
    given()
      .when()
      .get("/body-from-path-param/pathParamValue")
      .`then`()
      .body("attr", equalTo("pathParamValue"))
      .statusCode(200)
  }

  @Test
  def shouldSetBodyAttributeFromQueryParam(): Unit = {
    given()
      .queryParam("param", "one", "two", "six")
      .when()
      .get("/body-from-query-param")
      .`then`()
      .body("attr", equalTo(asJavaArray(List("one", "two", "six"))))
      .statusCode(200)
  }

  @Test
  def shouldSetBodyAttributeFromCookie(): Unit = {
    given()
      .cookie("param", "cookieParamValue")
      .when()
      .get("/body-from-cookie")
      .`then`()
      .body("attr", equalTo("cookieParamValue"))
      .statusCode(200)
  }

    @Test
    def shouldSetBodyAttributeFromConf(): Unit = {
      given()
        .when()
        .get("/body-from-conf")
        .`then`()
        .body("attr", equalTo("confParamValue"))
        .statusCode(200)
    }


  @Test
  def shouldSetFixedHeader(): Unit = {
    given()
      .when()
      .get("/fixed-header")
      .`then`()
      .header("H", "value")
      .statusCode(200)
  }

  @Test
  def shouldSetHeaderFromDifferentResponseHeader(): Unit = {
    given()
      .when()
      .get("/header-from-different-response-header")
      .`then`()
      .header("H", "12345")
      .statusCode(200)
  }

  @Test
  def shouldSetHeaderFromPathParam(): Unit = {
    given()
      .when()
      .get("/header-from-path-param/pathParamValue")
      .`then`()
      .header("H", "pathParamValue")
      .statusCode(200)
  }

  @Test
  def shouldSetHeaderFromQueryParam(): Unit = {
    given()
      .when()
      .queryParam("param", "queryParamValue")
      .get("/header-from-query-param")
      .`then`()
      .header("H", "queryParamValue")
      .statusCode(200)
  }

  @Test
  def shouldSetHeaderFromCookie(): Unit = {
    given()
      .when()
      .cookie("param", "cookieParamValue")
      .get("/header-from-cookie")
      .`then`()
      .header("H", "cookieParamValue")
      .statusCode(200)
  }

  @Test
  def shouldSetHeaderFromResponseBody(): Unit = {
    given()
      .when()
      .get("/header-from-response-body")
      .`then`()
      .header("H", "bodyParamValue")
      .statusCode(200)
  }

  @Test
  def shouldSetHeaderFromConf(): Unit = {
    given()
      .when()
      .get("/header-from-conf")
      .`then`()
      .header("H", "confParamValue")
      .statusCode(200)
  }

  @Test
  def shouldSetHeaderFromDynamicScopeAndFindMultipleParamsInPatternAndReorderThemInOutput(): Unit = {
    given()
      .when()
      .get("/dyn-header-can-find-multiple-params-in-pattern-and-reorder-them-in-input")
      .`then`()
      .header("X-Transaction", equalTo(s"$swiftId.$transactionId"))
      .statusCode(200)
  }

  @Test
  def shouldSetHeaderWhenPatternMatchesEntireValueButNotWhenItMatchesPartOfAWiderValue(): Unit = {
    given()
      .when()
    .get("/dyn-header-requires-pattern-matching-on-entire-value")
      .`then`()
      .header("X-Transaction", equalTo(s"$transactionId/swift.$swiftId"))
      .header("X-Payment", equalTo(null))
      .header("X-Env", equalTo(s"$envIdSuffixed"))
  }

  @Test
  def shouldSetHeaderFromDynamicScopeUsingAllOfTheValuesWhichMatchThePattern(): Unit = {
    given()
      .when()
      .get("/dyn-header-will-obtain-all-the-values-matching-the-pattern")
      .getHeaders.getValues("X-Id") mustBe asJavaArray(List(s"$idOne", s"$idTwo"))
  }
}
