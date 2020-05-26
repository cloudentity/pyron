package com.cloudentity.pyron.plugin.verticle

import java.util.{Optional, UUID}

import io.circe.generic.semiauto._
import io.circe.{Decoder, Json}
import com.cloudentity.pyron.domain.flow.{GroupMatchCriteria, PathPattern, PathPrefix, PluginConf, ApiGroupPluginConf, PluginName, RequestCtx, ServiceClientName}
import com.cloudentity.pyron.domain.openapi.{DiscoverableServiceId, OpenApiRule}
import com.cloudentity.pyron.plugin.RequestPluginService
import com.cloudentity.pyron.plugin.bus.request._
import com.cloudentity.pyron.plugin.config.{ValidateError, ValidateOk, ValidateRequest, ValidateResponse}
import com.cloudentity.pyron.plugin.openapi.{ConvertOpenApiError, ConvertOpenApiRequest, ConvertOpenApiResponse, ConvertedOpenApi}
import com.cloudentity.pyron.test.TestRequestResponseCtx
import com.cloudentity.tools.vertx.bus.ServiceClientFactory
import com.cloudentity.tools.vertx.test.ScalaVertxUnitTest
import com.cloudentity.tools.vertx.tracing.TracingContext
import com.cloudentity.tools.vertx.verticles.VertxDeploy
import io.swagger.models.Swagger
import io.vertx.core.http.HttpMethod
import org.junit.Test
import org.scalatest.MustMatchers

import scala.concurrent.Future
import io.vertx.core.{Future => VxFuture}
import io.vertx.ext.unit.TestContext

class RequestPluginVerticleTest extends ScalaVertxUnitTest with MustMatchers with TestRequestResponseCtx {
  case class DummyConfig(x: String, y: String)
  class DummyPlugin extends RequestPluginVerticle[DummyConfig] with RequestPluginService {
    val _name = UUID.randomUUID().toString
    override def name: PluginName = PluginName(_name)
    override def apply(ctx: RequestCtx, conf: DummyConfig): Future[RequestCtx] = ???
    override def validate(c: DummyConfig): ValidateResponse = ???
    override def confDecoder: Decoder[DummyConfig] = deriveDecoder
  }

  def dummyConf(pluginName: PluginName) = ApiGroupPluginConf(pluginName, Json.fromFields(List("x" -> Json.fromString("x"), "y" -> Json.fromString("y"))), None)

  private def createClient(plugin: DummyPlugin) = {
    ServiceClientFactory.make(vertx.eventBus(), classOf[RequestPluginService], Optional.of(plugin.name.value))
  }

  @Test
  def responsePluginVerticleValidateConfigShouldReturnValidateErrorWhenConfigDecodingError(ctx: TestContext): Unit = {
    // given
    val plugin = new DummyPlugin
    val pluginClient = createClient(plugin)
    val conf = ApiGroupPluginConf(plugin.name, Json.fromFields(Nil), None)

    VertxDeploy.deploy(vertx, plugin)
      .compose { _ =>
        // when
        pluginClient.validateConfig(ValidateRequest(conf))
      }.compose { response =>
      // then
      response match {
        case ValidateError(_) => // ok
        case x => fail(x.toString)
      }
      VxFuture.succeededFuture(())
    }.onComplete(ctx.asyncAssertSuccess())
  }

  @Test
  def responsePluginVerticleValidateConfigShouldReturnValidateErrorWhenExceptionThrown(ctx: TestContext): Unit = {
    // given
    val plugin = new DummyPlugin with RequestPluginService {
      override def validate(c: DummyConfig): ValidateResponse = throw new Exception("error")
    }
    val pluginClient = createClient(plugin)
    val conf = dummyConf(plugin.name)

    VertxDeploy.deploy(vertx, plugin)
      .compose { _ =>
        // when
        pluginClient.validateConfig(ValidateRequest(conf))
      }.compose { response =>
      // then
      response match {
        case ValidateError("error") => // ok
        case x => fail(x.toString)
      }
      VxFuture.succeededFuture(())
    }.onComplete(ctx.asyncAssertSuccess())
  }

  @Test
  def responsePluginVerticleValidateConfigShouldReturnValidateOkOnSuccess(ctx: TestContext): Unit = {
    // given
    val plugin = new DummyPlugin with RequestPluginService {
      override def validate(c: DummyConfig): ValidateResponse = ValidateOk
    }
    val pluginClient = createClient(plugin)
    val conf = dummyConf(plugin.name)

    VertxDeploy.deploy(vertx, plugin)
      .compose { _ =>
        // when
        pluginClient.validateConfig(ValidateRequest(conf))
      }.compose { response =>

      // then
      response match {
        case ValidateOk => // ok
        case x => fail(x.toString)
      }

      VxFuture.succeededFuture(())
    }.onComplete(ctx.asyncAssertSuccess())
  }

  @Test
  def responsePluginVerticleApplyShouldReturnErrorWhenExceptionThrown(ctx: TestContext): Unit = {
    // given
    val plugin = new DummyPlugin with RequestPluginService {
      override def apply(ctx: RequestCtx, conf: DummyConfig): Future[RequestCtx] = throw new Exception("error")
    }
    val pluginClient = createClient(plugin)
    val conf = dummyConf(plugin.name)
    VertxDeploy.deploy(vertx, plugin)
      .compose { _ =>
        // when
        pluginClient.applyPlugin(TracingContext.dummy(), ApplyRequest(emptyRequestCtx, conf))
      }.compose { response =>
      // then
      response match {
        case ApplyError(_) => // ok
        case x => fail(x.toString)
      }

      VxFuture.succeededFuture(())
    }.onComplete(ctx.asyncAssertSuccess())
  }

  @Test
  def responsePluginVerticleApplyShouldReturnErrorWhenConfigDecodingError(ctx: TestContext): Unit = {
    // given
    val plugin = new DummyPlugin
    val pluginClient = createClient(plugin)
    val conf = dummyConf(plugin.name).copy(conf = Json.fromFields(Nil))
    VertxDeploy.deploy(vertx, plugin)
      .compose { _ =>
        // when
        pluginClient.applyPlugin(TracingContext.dummy(), ApplyRequest(emptyRequestCtx, conf))
      }.compose { response =>
      // then
      response match {
        case ApplyError(_) => // ok
        case x => fail(x.toString)
      }

      VxFuture.succeededFuture(())
    }.onComplete(ctx.asyncAssertSuccess())
  }

  @Test
  def responsePluginVerticleApplyShouldReturnErrorWhenApplyFailed(ctx: TestContext): Unit = {
    // given
    val plugin = new DummyPlugin with RequestPluginService {
      override def apply(ctx: RequestCtx, conf: DummyConfig): Future[RequestCtx] = Future.failed(new Exception("error"))
    }
    val pluginClient = createClient(plugin)
    val conf = dummyConf(plugin.name)

    VertxDeploy.deploy(vertx, plugin)
      .compose { _ =>
        // when
        pluginClient.applyPlugin(TracingContext.dummy(), ApplyRequest(emptyRequestCtx, conf))
      }.compose { response =>
      // then
      response match {
        case ApplyError(_) => // ok
        case x => fail(x.toString)
      }

      VxFuture.succeededFuture(())
    }.onComplete(ctx.asyncAssertSuccess())
  }

  @Test
  def responsePluginVerticleApplyShouldReturnRequestCtxWhenSuccess(ctx: TestContext): Unit = {
    // given
    val plugin = new DummyPlugin with RequestPluginService {
      override def apply(ctx: RequestCtx, conf: DummyConfig): Future[RequestCtx] = Future.successful(ctx)
    }
    val pluginClient = createClient(plugin)
    val conf = dummyConf(plugin.name)

    VertxDeploy.deploy(vertx, plugin)
      .compose { _ =>
        // when
        pluginClient.applyPlugin(TracingContext.dummy(), ApplyRequest(emptyRequestCtx, conf))
      }.compose { response =>
      // then
      response match {
        case Continue(request) => // ok
        case x => fail(x.toString)
      }
      VxFuture.succeededFuture(())
    }.onComplete(ctx.asyncAssertSuccess())
  }

  @Test
  def responsePluginVerticleApplyShouldCachePluginConfig(ctx: TestContext): Unit = {
    // given
    var decodes = 0
    val plugin = new DummyPlugin with RequestPluginService {
      override def apply(ctx: RequestCtx, conf: DummyConfig): Future[RequestCtx] = Future.successful(ctx)
      override def decodeRuleConf(rawConf: Json) = {
        decodes += 1
        super.decodeRuleConf(rawConf)
      }
    }
    val pluginClient = createClient(plugin)
    val conf = dummyConf(plugin.name)

    VertxDeploy.deploy(vertx, plugin)
      .compose { _ => pluginClient.applyPlugin(TracingContext.dummy(), ApplyRequest(emptyRequestCtx, conf)) }
      .compose { _ => pluginClient.applyPlugin(TracingContext.dummy(), ApplyRequest(emptyRequestCtx, conf)) }
      .compose { response =>
        // then
        response match {
          case Continue(request) => decodes must be(1)
          case x => fail(x.toString)
        }

        VxFuture.succeededFuture(())
      }.onComplete(ctx.asyncAssertSuccess())
  }

  val openApiRule = OpenApiRule(HttpMethod.GET, DiscoverableServiceId(ServiceClientName("service")), GroupMatchCriteria.empty, PathPattern("/x"), PathPrefix(""), false, None, None, Nil, Nil, None)

  @Test
  def requestPluginVerticleConvertOpenApiShouldReturnConvertOpenApiErrorWhenConfigDecodingError(ctx: TestContext): Unit = {
    // given
    val plugin = new DummyPlugin
    val pluginClient = createClient(plugin)
    val conf = ApiGroupPluginConf(plugin.name, Json.fromFields(Nil), None)

    VertxDeploy.deploy(vertx, plugin)
      .compose { _ => pluginClient.convertOpenApi(TracingContext.dummy(), ConvertOpenApiRequest(new Swagger(), openApiRule, conf)) }
      .compose { response =>
        // then
        response match {
          case ConvertOpenApiError(_) => // ok
          case x => fail(x.toString)
        }

        VxFuture.succeededFuture(())
      }.onComplete(ctx.asyncAssertSuccess())
  }

  @Test
  def requestPluginVerticleConvertOpenApiShouldReturnConvertOpenApiErrorWhenExceptionThrown(ctx: TestContext): Unit = {
    // given
    val plugin = new DummyPlugin with RequestPluginService {
      override def convertOpenApi(openApi: Swagger, rule: OpenApiRule, c: DummyConfig): ConvertOpenApiResponse = throw new Exception("error")
    }
    val pluginClient = createClient(plugin)
    val conf = dummyConf(plugin.name)

    VertxDeploy.deploy(vertx, plugin)
      .compose { _ => pluginClient.convertOpenApi(TracingContext.dummy(), ConvertOpenApiRequest(new Swagger(), openApiRule, ApiGroupPluginConf(conf.name, conf.conf, None))) }
      .compose { response =>
        // then
        response match {
          case ConvertOpenApiError("error") => // ok
          case x => fail(x.toString)
        }

        VxFuture.succeededFuture(())
      }.onComplete(ctx.asyncAssertSuccess())
  }

  @Test
  def requestPluginVerticleConvertOpenApiShouldReturnConvertedOpenApiWhenSuccess(ctx: TestContext): Unit = {
    // given
    val plugin = new DummyPlugin with RequestPluginService {
      override def convertOpenApi(openApi: Swagger, rule: OpenApiRule, c: DummyConfig): ConvertOpenApiResponse = ConvertedOpenApi(openApi.basePath("/base-path"))
    }
    val pluginClient = createClient(plugin)
    val conf = dummyConf(plugin.name)

    VertxDeploy.deploy(vertx, plugin)
      .compose { _ => pluginClient.convertOpenApi(TracingContext.dummy(), ConvertOpenApiRequest(new Swagger(), openApiRule, ApiGroupPluginConf(conf.name, conf.conf, None))) }
      .compose { response =>
        // then
        response match {
          case ConvertedOpenApi(s) => s.getBasePath must be("/base-path")
          case x => fail(x.toString)
        }

        VxFuture.succeededFuture(())
      }.onComplete(ctx.asyncAssertSuccess())
  }
}
