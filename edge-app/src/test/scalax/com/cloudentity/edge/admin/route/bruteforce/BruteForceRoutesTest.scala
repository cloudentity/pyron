package com.cloudentity.edge.admin.route.bruteforce

import java.time.Instant

import com.cloudentity.tools.api.errors.ApiError
import io.circe.parser._
import io.circe.syntax._
import com.cloudentity.edge.plugin.impl.bruteforce.{BruteForceAttempt, BruteForcePlugin}
import com.cloudentity.tools.test.utils.VertxServerTest
import com.cloudentity.tools.vertx.hazelcast.{HazelcastService, HazelcastServiceClient}
import com.cloudentity.tools.vertx.server.api.routes.utils.RouteOperations
import com.cloudentity.tools.vertx.server.api.routes.{RouteService, ScalaRouteVerticle}
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.vertx.ext.web.RoutingContext
import org.hamcrest.core.IsEqual
import org.hamcrest.{BaseMatcher, Description}
import org.junit.Test

import scala.concurrent.Future

class BruteForceRoutesTest extends VertxServerTest {
  override def getMainVerticle: String = "com.cloudentity.edge.Application"

  override def getMetaConfPath() = "src/test/resources/admin/bruteforce/meta-config.json"

  @Test
  def shouldGetAndDeleteUserBruteForceAttempts(): Unit = {
    val timestamp = 100000

    // create brute-force attempts
    given()
    .when()
      .post(s"/bruteforce/idenfitierpassword/identifier/joe@cloudentity.com/1/$timestamp")
    .`then`()
        .statusCode(200)

    // GetUserBruteForceAttemptsRoute
    given()
    .when()
      .get("/bruteforce/idenfitierpassword/identifier/joe@cloudentity.com")
    .`then`()
      .statusCode(200)
      .body(new IsEqual(BruteForceResponse(false,  List(BruteForceAttempt(false, Instant.ofEpochMilli(timestamp), 10000))).asJson.noSpaces))

    // DeleteUserBruteForceAttemptsRoute
    given()
    .when()
      .delete(s"/bruteforce/idenfitierpassword/identifier/joe@cloudentity.com")
    .`then`()
      .statusCode(204)

    // verify DeleteUserBruteForceAttemptsRoute
    given()
    .when()
      .get("/bruteforce/idenfitierpassword/identifier/joe@cloudentity.com")
    .`then`()
      .statusCode(404)
  }

  @Test
  def shouldListBruteForceAttemptsCounterNames(): Unit = {
    val adminPort = RestAssured.port
    RestAssured.port = 7770

    try {
      // create brute-force attempts
      given()
      .when()
        .header("identifier", "user-1")
        .get(s"/brute-force-protected-1")
      .`then`()
        .statusCode(200)

      given()
      .when()
        .header("identifier", "user-1")
        .get(s"/brute-force-protected-2")
      .`then`()
        .statusCode(200)
    } finally {
      RestAssured.port = adminPort
    }

    // get names of brute force counters
    given()
    .when()
      .get("/bruteforce/config")
    .`then`()
      .statusCode(200)
      .body(new BaseMatcher[String] {
        override def matches(o: scala.Any): Boolean =
          decode[Map[String, Set[String]]](o.toString).toOption
            .map(_ == Map("apiSignatures" -> Set("brute-force-protected-1", "brute-force-protected-2")))
            .getOrElse(false)

        override def describeTo(description: Description): Unit = ()
      })
  }

  @Test
  def shouldGetAndDeleteBruteForceAttempts(): Unit = {
    val timestamp = 100000

    // create brute-force attempts
    given()
    .when()
      .post(s"/bruteforce/idenfitierpassword/identifier/joe@cloudentity.com/1/$timestamp")
    .`then`()
      .statusCode(200)

    // GetUserBruteForceAttemptsRoute
    given()
    .when()
      .get("/bruteforce/idenfitierpassword/identifier/joe@cloudentity.com")
    .`then`()
      .statusCode(200)
      .body(new IsEqual(BruteForceResponse(false, List(BruteForceAttempt(false, Instant.ofEpochMilli(timestamp), 10000))).asJson.noSpaces))

    // DeleteBruteForceAttemptsRoute
    given()
    .when()
      .delete(s"/bruteforce/idenfitierpassword/identifiers")
    .`then`()
      .statusCode(204)

    // verify DeleteBruteForceAttemptsRoute
    given()
    .when()
      .get("/bruteforce/idenfitierpassword/identifier/joe@cloudentity.com")
    .`then`()
      .statusCode(404)
  }
}

class SetBruteForceCounterTestRoute extends ScalaRouteVerticle with RouteService with RouteOperations {
  var cache: HazelcastServiceClient = _

  override def initService(): Unit = {
    cache = HazelcastServiceClient(createClient(classOf[HazelcastService]))
  }

  override protected def handle(ctx: RoutingContext): Unit = {
    val program: Future[ApiError \/ Unit] = {
      for {
        counterName <- getPathParam(ctx, "counterName")
        identifier  <- getPathParam(ctx, "identifier")
        count       <- getPathParam(ctx, "count")
        timestamp   <- getPathParam(ctx, "timestamp")
        _           <- cache.setValue(BruteForcePlugin.cacheCollectionPrefix + counterName, identifier, createBruteForceAttempts(count, timestamp)).toOperation
      } yield ()
    }.run

    handleCompleteNoBodyS(ctx, OK)(program)
  }

  private def createBruteForceAttempts(count: String, timestamp: String) =
    List.fill(count.toInt)(BruteForceAttempt(false, Instant.ofEpochMilli(timestamp.toLong), 10000))
}
