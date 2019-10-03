package com.cloudentity.edge.plugin.verticle

import java.util.{Optional, UUID}

import io.circe.generic.semiauto._
import io.circe.{Decoder, Json}
import com.cloudentity.edge.domain._
import com.cloudentity.edge.domain.flow.{PluginConf, PluginName, ResponseCtx}
import com.cloudentity.edge.plugin.ResponsePluginService
import com.cloudentity.edge.plugin.bus.response._
import com.cloudentity.edge.plugin.config._
import com.cloudentity.edge.test.TestRequestResponseCtx
import com.cloudentity.tools.vertx.bus.ServiceClientFactory
import com.cloudentity.tools.vertx.scala.FutureConversions
import com.cloudentity.tools.vertx.test.ScalaVertxUnitTest
import com.cloudentity.tools.vertx.tracing.TracingContext
import com.cloudentity.tools.vertx.verticles.VertxDeploy
import io.vertx.ext.unit.TestContext
import org.junit.Test
import org.scalatest.MustMatchers

import scala.concurrent.Future
import io.vertx.core.{Future => VxFuture}

class ResponsePluginVerticleTest extends ScalaVertxUnitTest with MustMatchers with TestRequestResponseCtx with FutureConversions {
  case class DummyConfig(x: String, y: String)
  class DummyPlugin extends ResponsePluginVerticle[DummyConfig] with ResponsePluginService {
    val _name = UUID.randomUUID().toString
    override def name: PluginName = PluginName(_name)
    override def apply(ctx: ResponseCtx, conf: DummyConfig): Future[ResponseCtx] = ???
    override def validate(c: DummyConfig): ValidateResponse = ???
    override def confDecoder: Decoder[DummyConfig] = deriveDecoder
  }

  def dummyConf(pluginName: PluginName) = PluginConf(pluginName, Json.fromFields(List("x" -> Json.fromString("x"), "y" -> Json.fromString("y"))))

  private def createClient(plugin: DummyPlugin) = {
    ServiceClientFactory.make(vertx.eventBus(), classOf[ResponsePluginService], Optional.of(plugin.name.value))
  }

  @Test
  def responsePluginVerticleValidateConfigShouldReturnValidateErrorWhenConfigDecodingError(ctx: TestContext): Unit = {
      // given
      val plugin = new DummyPlugin
      val pluginClient = createClient(plugin)
      val conf = PluginConf(plugin.name, Json.fromFields(Nil))

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
      }.setHandler(ctx.asyncAssertSuccess())
    }

  @Test
  def responsePluginVerticleValidateConfigShouldReturnValidateErrorWhenExceptionThrown(ctx: TestContext): Unit = {
      // given
      val plugin = new DummyPlugin with ResponsePluginService {
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
        }.setHandler(ctx.asyncAssertSuccess())
    }

  @Test
  def responsePluginVerticleValidateConfigShouldReturnValidateOkOnSuccess(ctx: TestContext): Unit = {
      // given
      val plugin = new DummyPlugin with ResponsePluginService {
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
        }.setHandler(ctx.asyncAssertSuccess())
    }

  @Test
  def responsePluginVerticleApplyShouldReturnErrorWhenExceptionThrown(ctx: TestContext): Unit = {
      // given
      val plugin = new DummyPlugin with ResponsePluginService {
        override def apply(ctx: ResponseCtx, conf: DummyConfig): Future[ResponseCtx] = throw new Exception("error")
      }
      val pluginClient = createClient(plugin)
      val conf = dummyConf(plugin.name)
      VertxDeploy.deploy(vertx, plugin)
        .compose { _ =>
          // when
          pluginClient.applyPlugin(TracingContext.dummy(), ApplyRequest(emptyResponseCtx, conf))
        }.compose { response =>
          // then
          response match {
            case ApplyError(_) => // ok
            case x => fail(x.toString)
          }

          VxFuture.succeededFuture(())
      }.setHandler(ctx.asyncAssertSuccess())
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
          pluginClient.applyPlugin(TracingContext.dummy(), ApplyRequest(emptyResponseCtx, conf))
        }.compose { response =>
          // then
          response match {
            case ApplyError(_) => // ok
            case x => fail(x.toString)
          }

          VxFuture.succeededFuture(())
        }.setHandler(ctx.asyncAssertSuccess())
    }

  @Test
  def responsePluginVerticleApplyShouldReturnErrorWhenApplyFailed(ctx: TestContext): Unit = {
      // given
      val plugin = new DummyPlugin with ResponsePluginService {
        override def apply(ctx: ResponseCtx, conf: DummyConfig): Future[ResponseCtx] = Future.failed(new Exception("error"))
      }
      val pluginClient = createClient(plugin)
      val conf = dummyConf(plugin.name)
    
      VertxDeploy.deploy(vertx, plugin)
        .compose { _ =>
          // when
          pluginClient.applyPlugin(TracingContext.dummy(), ApplyRequest(emptyResponseCtx, conf))
        }.compose { response =>
          // then
          response match {
            case ApplyError(_) => // ok
            case x => fail(x.toString)
          }

          VxFuture.succeededFuture(())
        }.setHandler(ctx.asyncAssertSuccess())
    }

  @Test
  def responsePluginVerticleApplyShouldReturnRequestCtxWhenSuccess(ctx: TestContext): Unit = {
      // given
      val plugin = new DummyPlugin with ResponsePluginService {
        override def apply(ctx: ResponseCtx, conf: DummyConfig): Future[ResponseCtx] = Future.successful(ctx)
      }
      val pluginClient = createClient(plugin)
      val conf = dummyConf(plugin.name)

      VertxDeploy.deploy(vertx, plugin)
        .compose { _ =>
          // when
          pluginClient.applyPlugin(TracingContext.dummy(), ApplyRequest(emptyResponseCtx, conf))
        }.compose { response =>
          // then
          response match {
            case Continue(request) => // ok
            case x => fail(x.toString)
          }
          VxFuture.succeededFuture(())
        }.setHandler(ctx.asyncAssertSuccess())
    }

  @Test
  def responsePluginVerticleApplyShouldCachePluginConfig(ctx: TestContext): Unit = {
      // given
      var decodes = 0
      val plugin = new DummyPlugin with ResponsePluginService {
        override def apply(ctx: ResponseCtx, conf: DummyConfig): Future[ResponseCtx] = Future.successful(ctx)
        override def decodeRuleConf(rawConf: Json) = {
          decodes += 1
          super.decodeRuleConf(rawConf)
        }
      }
      val pluginClient = createClient(plugin)
      val conf = dummyConf(plugin.name)

      VertxDeploy.deploy(vertx, plugin)
        .compose { _ => pluginClient.applyPlugin(TracingContext.dummy(), ApplyRequest(emptyResponseCtx, conf)) }
        .compose { _ => pluginClient.applyPlugin(TracingContext.dummy(), ApplyRequest(emptyResponseCtx, conf)) }
        .compose { response =>
          // then
          response match {
            case Continue(request) => decodes must be(1)
            case x => fail(x.toString)
          }

          VxFuture.succeededFuture(())
        }.setHandler(ctx.asyncAssertSuccess())
    }
}
