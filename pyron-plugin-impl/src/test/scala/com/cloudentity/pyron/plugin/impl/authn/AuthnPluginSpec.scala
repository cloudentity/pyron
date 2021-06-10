package com.cloudentity.pyron.plugin.impl.authn

import com.cloudentity.pyron.domain.flow.{AuthnCtx, RequestCtx}
import com.cloudentity.pyron.domain.http
import com.cloudentity.pyron.domain.http.ApiResponse
import com.cloudentity.pyron.plugin.config._
import com.cloudentity.pyron.plugin.impl.authn.AuthnPlugin._
import com.cloudentity.pyron.test.TestRequestResponseCtx
import com.cloudentity.tools.vertx.http.Headers
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.circe.{Json, JsonObject}
import io.vertx.core.{Future, Vertx}
import io.vertx.core.buffer.Buffer
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class AuthnPluginSpec extends WordSpec with MustMatchers with TestRequestResponseCtx {

  implicit lazy val ec: VertxExecutionContext = VertxExecutionContext(Vertx.vertx().getOrCreateContext())

  val req: RequestCtx = emptyRequestCtx

  def authnProviderF(f: RequestCtx => Future[Option[AuthnProviderResult]]): AuthnProvider = new AuthnProvider {
    override def authenticate(req: RequestCtx, methodConf: AuthnMethodConf): Future[Option[AuthnProviderResult]] = f(req)
    override def tokenType(): Future[String] = Future.succeededFuture("test1")
  }

  def authnProvider(f: RequestCtx => Option[AuthnProviderResult]): AuthnProvider = new AuthnProvider {
    override def authenticate(req: RequestCtx, methodConf: AuthnMethodConf): Future[Option[AuthnProviderResult]] = Future.succeededFuture(f(req))
    override def tokenType(): Future[String] = Future.succeededFuture("test2")
  }

  def entityProviderF(f: AuthnCtx => Future[AuthnCtx]): EntityProvider =
    (_: TracingContext, ctx: AuthnCtx) => f(ctx)

  def entityProvider(f: AuthnCtx => AuthnCtx): EntityProvider =
    (_: TracingContext, ctx: AuthnCtx) => Future.succeededFuture(f(ctx))

  "AuthnPluginWorker" should {

    "return TargetRequest with no authn-provider claims WHEN authn success" in {
      // given
      val userObject = Json.fromJsonObject(JsonObject.singleton("uuid", Json.fromString("xxx")))
      val authnProviders =
        Map(
          "authn-method" -> authnProvider(_ => Some(AuthnSuccess(AuthnCtx("user" -> userObject)))),
          "other-authn-method" -> authnProvider(_ => None)
        )

      val worker = new AuthnPluginWorker(authnProviders, Map())
      val conf = AuthnPluginConf(List("other-authn-method", "authn-method"), None, None, None, None, None)

      // when
      val result: RequestCtx = Await.result(worker.apply(req, conf), 1 second)

      // then
      result.isAborted mustBe false
      result.authnCtx.value must contain(("authnMethod", Json.fromString("authn-method")))
    }

    "return TargetRequest with no authn-provider claims WHEN authn success AND some authn method failed" in {
      // given
      val userObject = Json.fromJsonObject(JsonObject.singleton("uuid", Json.fromString("xxx")))
      val authnProviders =
        Map(
          "authn-method" -> authnProvider(_ => Some(AuthnSuccess(AuthnCtx("user" -> userObject)))),
          "failing-authn-method" -> authnProvider(_ => Some(AuthnFailure(ApiResponse(401, Buffer.buffer(), Headers()))))
        )

      val worker = new AuthnPluginWorker(authnProviders, Map())
      val conf = AuthnPluginConf(List("failing-authn-method", "authn-method"), None, None, None, None, None)

      // when
      val result: RequestCtx = Await.result(worker.apply(req, conf), 1 second)

      // then
      result.isAborted mustBe false
      result.authnCtx.value must contain(("authnMethod", Json.fromString("authn-method")))
    }

    "return 401 WHEN no authn method matching" in {
      // given
      val authnProviders =
        Map(
          "authn-method" -> authnProvider(_ => None)
        )

      val worker = new AuthnPluginWorker(authnProviders, Map())
      val conf = AuthnPluginConf(List("authn-method"), None, None, None, None, None)

      // when
      val result: RequestCtx = Await.result(worker.apply(req, conf), 1 second)

      // then
      result.isAborted mustBe true
      result.aborted.get.statusCode mustBe 401
    }

    "return ApiResponse WHEN authn method returned ApiResponse AND no authn method returned success" in {
      // given
      val authnProviders =
        Map(
          "authn-method" -> authnProvider(_ => Some(AuthnFailure(http.ApiResponse(400, Buffer.buffer(), Headers()))))
        )

      val worker = new AuthnPluginWorker(authnProviders, Map())
      val conf = AuthnPluginConf(List("authn-method"), None, None, None, None, None)

      // when
      val result: RequestCtx = Await.result(worker.apply(req, conf), 1 second)

      // then
      result.isAborted mustBe true
      result.aborted.get.statusCode mustBe 400
    }

    "recover authentication method provider failure by abstaining from decision" in {
      // given
      val authnProviders =
        Map(
          "authn-method-fail" -> authnProviderF(_ => Future.failedFuture("")),
          "authn-method-success" -> authnProvider(_ => Some(AuthnSuccess(AuthnCtx())))
        )

      val worker = new AuthnPluginWorker(authnProviders, Map())
      val conf = AuthnPluginConf(List("authn-method-fail", "authn-method-success"), None, None, None, None, None)

      // when
      val result: RequestCtx = Await.result(worker.apply(req, conf), 1 second)

      // then
      result.isAborted mustBe false
      result.authnCtx.value must contain(("authnMethod", Json.fromString("authn-method-success")))
    }

    "return 500 WHEN one of entity providers returned failed Future" in {
      // given
      val authnProviders =
        Map(
          "authn-method" -> authnProvider(_ => Some(AuthnSuccess(AuthnCtx())))
        )

      val entityProviders =
        Map(
          "authn-method" -> Map(
            "user" -> entityProviderF(_ => Future.failedFuture(""))
          )
        )

      val worker = new AuthnPluginWorker(authnProviders, entityProviders)
      val conf = AuthnPluginConf(List("authn-method"), Some(List("user")), None, None, None, None)

      // when
      val result: RequestCtx = Await.result(worker.apply(req, conf), 1 second)

      // then
      result.isAborted mustBe true
      result.aborted.get.statusCode mustBe 500
    }

    "return success with entity WHEN authn success and entity provider" in {
      // given
      val userObject = Json.fromJsonObject(JsonObject.singleton("uuid", Json.fromString("xxx")))
      val fullUserObject = Json.fromJsonObject(JsonObject.fromMap(Map("email" -> Json.fromString("email@com"), "uuid" -> Json.fromString("xxx"))))
      val authnProviders =
        Map(
          "authn-method" -> authnProvider(_ => Some(AuthnSuccess(AuthnCtx("user" -> userObject))))
        )

      val entityProviders =
        Map(
          "authn-method" -> Map(
            "user" -> entityProvider(_ => AuthnCtx("user" -> fullUserObject))
          )
        )

      val worker = new AuthnPluginWorker(authnProviders, entityProviders)
      val conf = AuthnPluginConf(List("authn-method"), Some(List("user")), None, None, None, None)

      // when
      val result: RequestCtx = Await.result(worker.apply(req, conf), 1 second)

      // then
      result.isAborted mustBe false
      result.authnCtx.value must contain(("user", userObject.deepMerge(fullUserObject)))
    }

    "return success with entity on configured context key WHEN authn success and entity provider" in {
      // given
      val userObject = Json.fromJsonObject(JsonObject.singleton("uuid", Json.fromString("xxx")))
      val fullUserObject = Json.fromJsonObject(JsonObject.fromMap(Map("email" -> Json.fromString("email@com"), "uuid" -> Json.fromString("xxx"))))
      val authnProviders =
        Map(
          "authn-method" -> authnProvider(_ => Some(AuthnSuccess(AuthnCtx("user" -> userObject))))
        )

      val entityProviders =
        Map(
          "authn-method" -> Map(
            "user" -> entityProvider(_ => AuthnCtx("user" -> fullUserObject))
          )
        )

      val worker = new AuthnPluginWorker(authnProviders, entityProviders)
      val conf = AuthnPluginConf(List("authn-method"), Some(List("user")), None, None, Some("ctx-key"), None)

      // when
      val result: RequestCtx = Await.result(worker.apply(req, conf), 1 second)

      // then
      result.isAborted mustBe false
      result.authnCtx.value.get("ctx-key").flatMap(_.asObject).get.toMap must contain(("user", userObject.deepMerge(fullUserObject)))
    }

    "return success with optional entity WHEN authn success and entity provider missing" in {
      // given
      val userObject = Json.fromJsonObject(JsonObject.singleton("uuid", Json.fromString("xxx")))
      val fullUserObject = Json.fromJsonObject(JsonObject.fromMap(Map("email" -> Json.fromString("email@com"), "uuid" -> Json.fromString("xxx"))))
      val authnProviders =
        Map(
          "authn-method" -> authnProvider(_ => Some(AuthnSuccess(AuthnCtx("user" -> userObject))))
        )

      val entityProviders =
        Map(
          "authn-method" -> Map(
            "user" -> entityProvider(_ => AuthnCtx("user" -> fullUserObject))
          )
        )

      val worker = new AuthnPluginWorker(authnProviders, entityProviders)
      val conf = AuthnPluginConf(List("authn-method"), Some(List("user")), Some(List("optionalEp")), None, None, None)

      // when
      val result: RequestCtx = Await.result(worker.apply(req, conf), 1 second)

      // then
      result.isAborted mustBe false
      result.authnCtx.value must contain(("user", userObject.deepMerge(fullUserObject)))
    }
  }



  "AuthnPluginWorker" should {
    "modify API response if authn failed" in {
      // given
      val authnProviders =
        Map(
          "authn-method-code" -> authnProvider { _ =>
            val modify = Modify(identity, _.copy(statusCode = 403))
            Some(AuthnFailure(ApiResponse(401, Buffer.buffer(), Headers()), modify))
          },
          "authn-method-header" -> authnProvider { _ =>
            val modify = Modify(identity, _.copy(headers = Headers("header" -> List("value"))))
            Some(AuthnFailure(ApiResponse(401, Buffer.buffer(), Headers()), modify))
          }
        )

      val worker = new AuthnPluginWorker(authnProviders, Map())
      val conf = AuthnPluginConf(List("authn-method-code", "authn-method-header"), None, None, None, None, None)

      // when
      val result: RequestCtx = Await.result(worker.apply(req, conf), 1 second)

      // then
      result.isAborted mustBe true
      result.aborted.get.statusCode mustBe 403
      result.aborted.get.headers.getValues("header") mustBe Some(List("value"))
    }

    "modify target request even if authn provider failed" in {
      // given
      val authnProviders =
        Map(
          "authn-method-failing" -> authnProvider { _ =>
            val modify = Modify(req => req.modifyRequest(_.copy(headers = Headers("header" -> List("value")))), _.copy(statusCode = 403))
            Some(AuthnFailure(ApiResponse(401, Buffer.buffer(), Headers()), modify))
          },
          "authn-method-success" -> authnProvider { _ =>
            Some(AuthnSuccess(AuthnCtx()))
          }
        )

      val worker = new AuthnPluginWorker(authnProviders, Map())
      val conf = AuthnPluginConf(List("authn-method-failing", "authn-method-success"), None, None, None, None, None)

      // when
      val result: RequestCtx = Await.result(worker.apply(req, conf), 1 second)

      // then
      result.isAborted mustBe false
      result.targetRequest.headers.getValues("header") mustBe Some(List("value"))
    }

    "modify target request even if authn provider succeeded" in {
      // given
      val authnProviders =
        Map(
          "authn-method-success" -> authnProvider { _ =>
            val modify = Modify(req => req.modifyRequest(_.copy(headers = Headers("header" -> List("value")))), _.copy(statusCode = 403))
            Some(AuthnSuccess(AuthnCtx(), modify))
          }
        )

      val worker = new AuthnPluginWorker(authnProviders, Map())
      val conf = AuthnPluginConf(List("authn-method-success"), None, None, None, None, None)

      // when
      val result: RequestCtx = Await.result(worker.apply(req, conf), 1 second)

      // then
      result.isAborted mustBe false
      result.targetRequest.headers.getValues("header") mustBe Some(List("value"))
    }
  }

  "AuthnPluginWorker" should {
    "return ValidateOk when conf with existing authn methods and entities" in {
      val authnProviders =
        Map(
          "authn-method" -> authnProvider(_ => None)
        )

      val entityProviders =
        Map(
          "authn-method" -> Map(
            "user" -> entityProvider(_ => AuthnCtx())
          )
        )

      val worker = new AuthnPluginWorker(authnProviders, entityProviders)
      val conf = AuthnPluginConf(List("authn-method"), Some(List("user")), None, None, None, None)

      // when
      val result: ValidateResponse = worker.validate(conf)

      // then
      result mustBe ValidateOk
    }

    "return ValidateFailure when conf without authn methods" in {
      val authnProviders =
        Map(
          "authn-method" -> authnProvider(_ => None)
        )

      val entityProviders =
        Map(
          "authn-method" -> Map(
            "user" -> entityProvider(_ => AuthnCtx())
          )
        )

      val worker = new AuthnPluginWorker(authnProviders, entityProviders)
      val conf = AuthnPluginConf(List(), Some(List("user")), None, None, None, None)

      // when
      val result: ValidateResponse = worker.validate(conf)

      // then
      result.isInstanceOf[ValidateFailure] mustBe true
    }

    "return ValidateFailure when conf with entities for method without entity providers" in {
      val authnProviders =
        Map(
          "authn-method" -> authnProvider(_ => None)
        )

      val worker = new AuthnPluginWorker(authnProviders, Map())
      val conf = AuthnPluginConf(List("authn-method"), Some(List("user")), None, None, None, None)

      // when
      val result: ValidateResponse = worker.validate(conf)

      // then
      result.isInstanceOf[ValidateFailure] mustBe true
    }

    "return ValidateFailure when conf with missing authn method" in {
      val authnProviders =
        Map(
          "authn-method" -> authnProvider(_ => None)
        )

      val entityProviders =
        Map(
          "authn-method" -> Map(
            "user" -> entityProvider(_ => AuthnCtx())
          )
        )

      val worker = new AuthnPluginWorker(authnProviders, entityProviders)
      val conf = AuthnPluginConf(List("authn-method", "missing-authn-method"), Some(List("user")), None, None, None, None)

      // when
      val result: ValidateResponse = worker.validate(conf)

      // then
      result.isInstanceOf[ValidateFailure] mustBe true
    }

    "return ValidateFailure when conf with missing entity for single authn method" in {
      val authnProviders =
        Map(
          "authn-method" -> authnProvider(_ => None)
        )

      val entityProviders =
        Map(
          "authn-method" -> Map(
            "user" -> entityProvider(_ => AuthnCtx())
          )
        )

      val worker = new AuthnPluginWorker(authnProviders, entityProviders)
      val conf = AuthnPluginConf(List("authn-method"), Some(List("user", "other-entity")), None, None, None, None)

      // when
      val result: ValidateResponse = worker.validate(conf)

      // then
      result.isInstanceOf[ValidateFailure] mustBe true
    }

    "return ValidateFailure when conf with missing entity for different authn methods" in {
      val authnProviders =
        Map(
          "authn-method" -> authnProvider(_ => None),
          "other-authn-method" -> authnProvider(_ => None)
        )

      val entityProviders =
        Map(
          "authn-method" -> Map(
            "user" -> entityProvider(_ => AuthnCtx())
          ),
          "other-authn-method" -> Map(
            "other-entity" -> entityProvider(_ => AuthnCtx())
          )
        )

      val worker = new AuthnPluginWorker(authnProviders, entityProviders)
      val conf = AuthnPluginConf(List("authn-method", "other-authn-method"), Some(List("user", "other-entity")), None, None, None, None)

      // when
      val result: ValidateResponse = worker.validate(conf)

      // then
      result.isInstanceOf[ValidateFailure] mustBe true
    }
  }
}