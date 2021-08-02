package com.cloudentity.pyron.plugin.verticle

import java.util.{Optional, UUID}

import io.circe.generic.semiauto._
import io.circe.{Decoder, Json}
import com.cloudentity.pyron.domain.flow.{ApiGroupPluginConf, GroupMatchCriteria, PathPattern, PathPrefix, PluginName, RequestCtx, ServiceClientName}
import com.cloudentity.pyron.domain.openapi.{DiscoverableServiceId, OpenApiRule}
import com.cloudentity.pyron.plugin.RequestPluginService
import com.cloudentity.pyron.plugin.bus.request._
import com.cloudentity.pyron.plugin.config.{ValidateError, ValidateFailure, ValidateOk, ValidateRequest, ValidateResponse}
import com.cloudentity.pyron.plugin.openapi.{ConvertOpenApiError, ConvertOpenApiRequest, ConvertOpenApiResponse, ConvertedOpenApi}
import com.cloudentity.pyron.test.TestRequestResponseCtx
import com.cloudentity.tools.vertx.bus.VertxEndpointClient
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
    val _name: String = UUID.randomUUID().toString
    override def name: PluginName = PluginName(_name)
    override def apply(ctx: RequestCtx, conf: DummyConfig): Future[RequestCtx] = throw new NotImplementedError()
    override def validate(c: DummyConfig): ValidateResponse = throw new NotImplementedError()
    override def confDecoder: Decoder[DummyConfig] = deriveDecoder
  }

  def dummyConf(pluginName: PluginName): ApiGroupPluginConf = ApiGroupPluginConf(
    name = pluginName,
    conf = Json.fromFields(List("x" -> Json.fromString("x"), "y" -> Json.fromString("y"))),
    applyIf = None,
    addressPrefixOpt = None
  )

  private def createClient(plugin: DummyPlugin) = {
    VertxEndpointClient.make(vertx, classOf[RequestPluginService], Optional.of(plugin.name.value))
  }

  @Test
  def responsePluginVerticleValidateConfigShouldReturnValidateErrorWhenConfigDecodingError(ctx: TestContext): Unit = {
    // given
    val plugin = new DummyPlugin
    val pluginClient = createClient(plugin)
    val conf = ApiGroupPluginConf(plugin.name, Json.fromFields(Nil), None, None)

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
  def requestPluginVerticleValidateApplyIfShouldReturnValidateFailureWhenConfigDecodingError(ctx: TestContext): Unit = {
    // given
    val plugin = new DummyPlugin
    val pluginClient = createClient(plugin)
    val conf = ApiGroupPluginConf(plugin.name, Json.Null, Some(Json.fromString("invalid")), None)

    VertxDeploy.deploy(vertx, plugin)
      .compose { _ =>
        // when
        pluginClient.validateApplyIf(ValidateRequest(conf))
      }.compose { response =>
      // then
      response match {
        case ValidateFailure(_) => // ok
        case x => fail(x.toString)
      }
      VxFuture.succeededFuture(())
    }.onComplete(ctx.asyncAssertSuccess())
  }


  @Test
  def requestPluginVerticleValidateApplyIfShouldReturnValidateOkOnSuccess(ctx: TestContext): Unit = {
    // given
    val plugin = new DummyPlugin with RequestPluginService {
      override def validate(c: DummyConfig): ValidateResponse = ValidateOk
    }
    val pluginClient = createClient(plugin)
    val conf = dummyConf(plugin.name).copy(applyIf = Option(Json.obj("in" -> Json.obj("array" -> Json.arr(), "value" -> Json.fromString("")))))

    VertxDeploy.deploy(vertx, plugin)
      .compose { _ =>
        // when
        pluginClient.validateApplyIf(ValidateRequest(conf))
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
        case Continue(_) => // ok
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
      override def decodeRuleConf(rawConf: Json): Either[Throwable, DummyConfig] = {
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
          case Continue(_) => decodes mustBe 1
          case x => fail(x.toString)
        }
        VxFuture.succeededFuture(())
      }.onComplete(ctx.asyncAssertSuccess())
  }

  val openApiRule: OpenApiRule = OpenApiRule(
    method = HttpMethod.GET,
    serviceId = DiscoverableServiceId(ServiceClientName("service")),
    group = GroupMatchCriteria.empty,
    pathPattern = PathPattern("/x"),
    pathPrefix = PathPrefix(""),
    dropPathPrefix = false,
    rewriteMethod = None,
    rewritePath = None,
    plugins = Nil,
    tags = Nil,
    operationId = None
  )

  @Test
  def requestPluginVerticleConvertOpenApiShouldReturnConvertOpenApiErrorWhenConfigDecodingError(ctx: TestContext): Unit = {
    // given
    val plugin = new DummyPlugin
    val pluginClient = createClient(plugin)
    val conf = ApiGroupPluginConf(plugin.name, Json.fromFields(Nil), None, None)

    VertxDeploy.deploy(vertx, plugin).compose { _ =>
      pluginClient.convertOpenApi(
        TracingContext.dummy(),
        ConvertOpenApiRequest(new Swagger(), openApiRule, conf))
    }.compose { response =>
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
      override def convertOpenApi(openApi: Swagger, rule: OpenApiRule, c: DummyConfig): ConvertOpenApiResponse =
        throw new Exception("error")
    }
    val pluginClient = createClient(plugin)
    val conf = dummyConf(plugin.name)

    VertxDeploy.deploy(vertx, plugin).compose { _ =>
      pluginClient.convertOpenApi(
        TracingContext.dummy(),
        ConvertOpenApiRequest(
          new Swagger(),
          openApiRule,
          ApiGroupPluginConf(conf.name, conf.conf, None, None)
        )
      )
    }.compose { response =>
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

    VertxDeploy.deploy(vertx, plugin).compose { _ =>
      pluginClient.convertOpenApi(
        TracingContext.dummy(),
        ConvertOpenApiRequest(new Swagger(), openApiRule, ApiGroupPluginConf(conf.name, conf.conf, None, None))
      )
    }.compose { response =>
      // then
      response match {
        case ConvertedOpenApi(s) => s.getBasePath mustBe "/base-path"
        case x => fail(x.toString)
      }
      VxFuture.succeededFuture(())
    }.onComplete(ctx.asyncAssertSuccess())
  }
}
