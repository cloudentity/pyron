package com.cloudentity.edge.plugin.authn

import java.time.{Duration, Instant, ZoneId}
import java.time.format.DateTimeFormatter

import com.cloudentity.services.lla.client.api.UserApiClient
import com.cloudentity.services.lla.client.api.UserApiClient._
import com.cloudentity.services.lla.client.model._
import com.cloudentity.services.openapi.tools.httpclient.vertxscala.ApiClientHelpers.ReqTransformer
import com.cloudentity.services.openapi.tools.httpclient.vertxscala.{ApiClientHelpers, ClientError}
import com.cloudentity.services.openapi.tools.httpclient.vertxscala.auth.JwtAuth
import io.circe.Json
import com.cloudentity.edge.api.Responses.Errors
import com.cloudentity.edge.domain._
import com.cloudentity.edge.plugin.impl.authn.AuthnPlugin.{AuthnFailure, AuthnProviderResult, AuthnSuccess}
import com.cloudentity.edge.plugin.impl.authn.{AuthnMethodConf, HmacHelper}
import com.cloudentity.edge.plugin.impl.authn.methods._
import com.cloudentity.edge.test.TestRequestResponseCtx
import com.cloudentity.tools.vertx.http.Headers
import com.cloudentity.tools.vertx.test.VertxUnitTest
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.vertx.core.{Future => VxFuture}
import io.vertx.ext.unit.TestContext
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import org.junit.{Before, Test}
import org.scalatest.MustMatchers
import scalaz.{-\/, \/, \/-}

class LegacyHmacPluginSpec extends VertxUnitTest with MustMatchers with TestRequestResponseCtx {

  val url = "http://iiam.example.com:8080/user?realm=ovl&limit=10"
  val format = "EEE, d MMM yyyy HH:mm:ss Z"
  val typ = "orchis-hmac"
  val uuid = "1234-1234-1234-1234"
  val apiKey = "some_apikey"
  val body = "{\"identifer\": \"abc@abc.pl\", \"password\": \"pass1234\"}"

  val hmac: HmacHelper = new HmacHelper(format, 10, "orchis-hmac")
  val formatter = DateTimeFormatter.ofPattern(format).withZone(ZoneId.of("UTC"))

  implicit var ec: VertxExecutionContext = _

  @Before
  def setup() = {
    ec = VertxExecutionContext(vertx.getOrCreateContext())
  }

  @Test
  def whenRequestMatchesAuthorizationHeaderThenHmacIsValid(ctx: TestContext): Unit = {
    val now = Instant.now()
    val req = hmac.buildRequest("POST", body, now, "http://iiam.example.com:8080/user?realm=ovl&limit=10") match {
      case -\/(_) => fail()
      case \/-(req) => req
    }

    val sign = hmac.buildSignature(apiKey.getBytes, req.getBytes) match {
      case -\/(_) => fail()
      case \/-(sign) => sign
    }

    val authToken = hmac.buildAuthorizationHeader(typ, uuid, sign)

    val encReq = hmac.encode(req) match {
      case -\/(_) => fail()
      case \/-(enc) => enc
    }

    val requestCtx = emptyRequestCtx.modifyRequest(
      _.copy(
        headers = Headers(
          "x-orchis-authorization" -> List(authToken),
          "x-orchis-request" -> List(encReq),
          "x-orchis-date" -> List(formatter.format(now))
        )
      )
    )

    providerWithMocks.authenticate(requestCtx, AuthnMethodConf(None))
      .compose { result: Option[AuthnProviderResult] =>
        ctx.assertTrue(result.isDefined)
        result match {
          case Some(AuthnSuccess(_, _)) => ctx.assertTrue(true)
          case _ => ctx.fail()
        }
        VxFuture.succeededFuture[Void]()
      }
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def whenRequestHasOldDateThenHmacOutdated(ctx: TestContext): Unit = {
    val now = Instant.now()
    val req = hmac.buildRequest("POST", body, now, "http://iiam.example.com:8080/user?realm=ovl&limit=10") match {
      case -\/(_) => fail()
      case \/-(req) => req
    }

    val sign = hmac.buildSignature(apiKey.getBytes, req.getBytes) match {
      case -\/(_) => fail()
      case \/-(sign) => sign
    }

    val authToken = hmac.buildAuthorizationHeader(typ, uuid, sign, "internal-applications")
    val encReq = hmac.encode(req) match {
      case -\/(_) => fail()
      case \/-(enc) => enc
    }

    val requestCtx = emptyRequestCtx.modifyRequest(
      _.copy(
        headers = Headers(
          "x-orchis-authorization" -> List(authToken),
          "x-orchis-request" -> List(encReq),
          "x-orchis-date" -> List(formatter.format(now.minus(Duration.ofMinutes(50))))
        )
      )
    )

    providerWithMocks.authenticate(requestCtx, AuthnMethodConf(None))
      .compose { result: Option[AuthnProviderResult] =>
        ctx.assertTrue(result.isDefined)
        result match {
          case Some(AuthnFailure(resp, _)) => resp mustBe(Errors.hmacRequestOutdated.toApiResponse)
          case a => ctx.fail()
        }
        VxFuture.succeededFuture[Void]()
      }
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def whenRequestHasInvalidHeaderThenInvalidCode(ctx: TestContext): Unit = {
    val now = Instant.now()
    val req = hmac.buildRequest("POST", body, now, "http://iiam.example.com:8080/user?realm=ovl&limit=10") match {
      case -\/(_) => fail()
      case \/-(req) => req
    }

    val authHeader = "invalid auth header"
    val encReq = hmac.encode(req) match {
      case -\/(_) => fail()
      case \/-(enc) => enc
    }

    val requestCtx = emptyRequestCtx.modifyRequest(
      _.copy(
        headers = Headers(
          "x-orchis-authorization" -> List(authHeader),
          "x-orchis-request" -> List(encReq),
          "x-orchis-date" -> List(formatter.format(now))
        )
      )
    )

    providerWithMocks.authenticate(requestCtx, AuthnMethodConf(None))
      .compose { result: Option[AuthnProviderResult] =>
        ctx.assertTrue(result.isDefined)
        result match {
          case Some(AuthnFailure(resp, _)) => resp mustBe(Errors.invalidRequest.toApiResponse)
          case a => ctx.fail()
        }
        VxFuture.succeededFuture[Void]()
      }
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def whenRequestHasInvalidDateThenInvalidCode(ctx: TestContext): Unit = {
    val now = Instant.now()
    val req = hmac.buildRequest("POST", body, now, "http://iiam.example.com:8080/user?realm=ovl&limit=10") match {
      case -\/(_) => fail()
      case \/-(req) => req
    }

    val sign = hmac.buildSignature(apiKey.getBytes, req.getBytes) match {
      case -\/(_) => fail()
      case \/-(sign) => sign
    }

    val authToken = hmac.buildAuthorizationHeader(typ, uuid, sign)
    val encReq = hmac.encode(req) match {
      case -\/(_) => fail()
      case \/-(enc) => enc
    }

    val requestCtx = emptyRequestCtx.modifyRequest(
      _.copy(
        headers = Headers(
          "x-orchis-authorization" -> List(authToken),
          "x-orchis-request" -> List(encReq),
          "x-orchis-date" -> List("invalidDate")
        )
      )
    )

    providerWithMocks.authenticate(requestCtx, AuthnMethodConf(None))
      .compose { result: Option[AuthnProviderResult] =>
        ctx.assertTrue(result.isDefined)
        result match {
          case Some(AuthnFailure(resp, _)) => resp mustBe(Errors.invalidRequest.toApiResponse)
          case a => ctx.fail()
        }
        VxFuture.succeededFuture[Void]()
      }
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def whenAuthHeaderHasInvalidSignatureThenInvalidCode(ctx: TestContext): Unit = {
    val now = Instant.now()
    val req = hmac.buildRequest("POST", body, now, "http://iiam.example.com:8080/user?realm=ovl&limit=10") match {
      case -\/(_) => fail()
      case \/-(req) => req
    }

    val authToken = hmac.buildAuthorizationHeader(typ, uuid, "invalidSignature")
    val encReq = hmac.encode(req) match {
      case -\/(_) => fail()
      case \/-(enc) => enc
    }

    val requestCtx = emptyRequestCtx.modifyRequest(
      _.copy(
        headers = Headers(
          "x-orchis-authorization" -> List(authToken),
          "x-orchis-request" -> List(encReq),
          "x-orchis-date" -> List(formatter.format(now))
        )
      )
    )

    providerWithMocks.authenticate(requestCtx, AuthnMethodConf(None))
      .compose { result: Option[AuthnProviderResult] =>
        ctx.assertTrue(result.isDefined)
        result match {
          case Some(AuthnFailure(resp, _)) => resp mustBe(Errors.hmacMismatch.toApiResponse)
          case a => ctx.fail()
        }
        VxFuture.succeededFuture[Void]()
      }
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def whenHeaderHasInvalidEncodedRequestThenInvalidCode(ctx: TestContext): Unit = {
    val now = Instant.now()
    val req = hmac.buildRequest("POST", body, now, "http://iiam.example.com:8080/user?realm=ovl&limit=10") match {
      case -\/(_) => fail()
      case \/-(req) => req
    }

    val sign = hmac.buildSignature(apiKey.getBytes, req.getBytes) match {
      case -\/(_) => fail()
      case \/-(sign) => sign
    }

    val authToken = hmac.buildAuthorizationHeader(typ, uuid, sign)

    val requestCtx = emptyRequestCtx.modifyRequest(
      _.copy(
        headers = Headers(
          "x-orchis-authorization" -> List(authToken),
          "x-orchis-request" -> List(""),
          "x-orchis-date" -> List(formatter.format(now))
        )
      )
    )

    providerWithMocks.authenticate(requestCtx, AuthnMethodConf(None))
      .compose { result: Option[AuthnProviderResult] =>
        ctx.assertTrue(result.isDefined)
        result match {
          case Some(AuthnFailure(resp, _)) => resp mustBe(Errors.hmacMismatch.toApiResponse)
          case a => ctx.fail()
        }
        VxFuture.succeededFuture[Void]()
      }
      .setHandler(ctx.asyncAssertSuccess())
  }


  def providerWithMocks(): LegacyHmacProvider = {
    val lhp = new LegacyHmacProvider()
    val config = LegacyHmacProviderConf("x-orchis-authorization", "x-orchis-request", "x-orchis-date", "apiKey", "orchis-hmac", "EEE, d MMM yyyy HH:mm:ss Z", 10, Some("internal-applications"), Some("abcdef12345667"))

    lhp.realm = "internal-applications"
    lhp.hmac = new HmacHelper(config.dateFormat, config.limitInMinutes, config.authHeaderPrefix)
    lhp.legacyHmacProviderConf = config
    lhp.baseHmacProviderConf = config
    lhp.jwtAuthenticator = mockJwt()
    lhp.userApi = mockUserApi()
    lhp.init(vertx(), vertx().getOrCreateContext())
    lhp.setup()

    return lhp
  }

  def mockJwt(): JwtAuth = new JwtAuth {
    override def auth(payload: Map[String, AnyRef]) = ApiClientHelpers.noopRequestTransformer
  }

  def mockUserApi(): UserApiClient = new UserApiClient {
    override def getUser(ctx: TracingContext, uuid: String, realm: Option[String]): VxFuture[\/[ClientError[GetUser.GetUserError], User]] = ???

    override def getUser(ctx: TracingContext, user: String, realm: Option[String], transform: ReqTransformer): VxFuture[\/[ClientError[GetUser.GetUserError], User]] = {
        VxFuture.succeededFuture[\/[ClientError[GetUser.GetUserError], User]](\/-(User(Map("apiKey" -> Json.fromString("salt123:Xa/CkFXgp+lX7plutJHaQx9vrjIPwmfFFrPNDPS+iPc=")))))
    }

    override def updateUser(ctx: TracingContext, uuid: String, body: UpdateBody, realm: Option[String]): VxFuture[\/[ClientError[UpdateUser.UpdateUserError], Unit]] = ???

    override def updateUser(ctx: TracingContext, uuid: String, body: UpdateBody, realm: Option[String], transform: ReqTransformer): VxFuture[\/[ClientError[UpdateUser.UpdateUserError], Unit]] = ???

    override def listUsers(ctx: TracingContext, body: FindBody, realm: Option[String]): VxFuture[\/[ClientError[ListUsers.ListUsersError], UsersList]] = ???

    override def listUsers(ctx: TracingContext, body: FindBody, realm: Option[String], transform: ReqTransformer): VxFuture[\/[ClientError[ListUsers.ListUsersError], UsersList]] = ???

    override def deleteUser(ctx: TracingContext, uuid: String, realm: Option[String]): VxFuture[\/[ClientError[DeleteUser.DeleteUserError], Unit]] = ???

    override def deleteUser(ctx: TracingContext, uuid: String, realm: Option[String], transform: ReqTransformer): VxFuture[\/[ClientError[DeleteUser.DeleteUserError], Unit]] = ???

    override def createUser(ctx: TracingContext, body: User, realm: Option[String]): VxFuture[\/[ClientError[CreateUser.CreateUserError], Uuid]] = ???

    override def createUser(ctx: TracingContext, body: User, realm: Option[String], transform: ReqTransformer): VxFuture[\/[ClientError[CreateUser.CreateUserError], Uuid]] = ???

  }
}
