package com.cloudentity.edge.openapi

import java.util

import com.cloudentity.edge.domain.flow.ServiceClientName
import com.cloudentity.edge.domain.openapi.{ConverterConf, DiscoverableServiceId, ProcessorsConf}
import com.cloudentity.tools.vertx.bus.ServiceClientFactory
import com.cloudentity.tools.vertx.conf.ConfVerticleDeploy
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.test.VertxUnitTest
import com.cloudentity.tools.vertx.tracing.TracingContext
import com.cloudentity.tools.vertx.verticles.VertxDeploy
import io.swagger.models.Swagger
import io.vertx.ext.unit.TestContext
import io.vertx.core.{Future => VxFuture}
import org.junit.Test

import scala.collection.JavaConverters._

class OpenApiProcessorsTest extends VertxUnitTest {
  @Test
  def multiplePreAndPostProcessorsShouldByApplied(ctx: TestContext) =
    processorsShouldByApplied(ctx, ProcessorsConf(Some(List("pre-a", "pre-b")), Some(List("post-a", "post-b")))) { swagger =>
      ctx.assertEquals(List("pre-a", "pre-b", "post-a", "post-b").asJava, swagger.getConsumes)
    }

  @Test
  def multiplePreProcessorsShouldByApplied(ctx: TestContext) =
    processorsShouldByApplied(ctx, ProcessorsConf(Some(List("pre-a", "pre-b")), None)) { swagger =>
      ctx.assertEquals(List("pre-a", "pre-b").asJava, swagger.getConsumes)
    }

  @Test
  def multiplePostProcessorsShouldByApplied(ctx: TestContext) =
    processorsShouldByApplied(ctx, ProcessorsConf(None, Some(List("post-a", "post-b")))) { swagger =>
      ctx.assertEquals(List("post-a", "post-b").asJava, swagger.getConsumes)
    }

  @Test
  def singlePreProcessorShouldByApplied(ctx: TestContext) =
    processorsShouldByApplied(ctx, ProcessorsConf(Some(List("pre-a")), None)) { swagger =>
      ctx.assertEquals(List("pre-a").asJava, swagger.getConsumes)
    }

  @Test
  def singlePostProcessorShouldByApplied(ctx: TestContext) =
    processorsShouldByApplied(ctx, ProcessorsConf(None, Some(List("post-a")))) { swagger =>
      ctx.assertEquals(List("post-a").asJava, swagger.getConsumes)
    }

  def processorsShouldByApplied(ctx: TestContext, conf: ProcessorsConf)(assertion: Swagger => Unit): Unit = {
    ConfVerticleDeploy.deployFileConfVerticle(vertx, "src/test/resources/openapi/converter.json")
      .compose { _ => VertxDeploy.deploy(vertx, new OpenApiConverterVerticle) }
      .compose { _ =>
        val converter = ServiceClientFactory.make(vertx.eventBus(), classOf[OpenApiConverter])
        val serviceId = DiscoverableServiceId(ServiceClientName("service-a"))
        val swagger = new Swagger()
        swagger.setPaths(new util.HashMap())

        converter.convert(TracingContext.dummy(), serviceId, swagger, Nil, ConverterConf(None, Some(conf)))
      }.compose { (swagger: Swagger) =>
        assertion(swagger)
        VxFuture.succeededFuture(())
      }.setHandler(ctx.asyncAssertSuccess())
  }
}

class TestPreAProcessor extends ScalaServiceVerticle with OpenApiPreProcessor {
  override def preProcess(openapi: Swagger): VxFuture[Swagger] =
    VxFuture.succeededFuture(openapi.consumes("pre-a"))
}

class TestPreBProcessor extends ScalaServiceVerticle with OpenApiPreProcessor {
  override def preProcess(openapi: Swagger): VxFuture[Swagger] =
    VxFuture.succeededFuture(openapi.consumes("pre-b"))
}

class TestPostAProcessor extends ScalaServiceVerticle with OpenApiPostProcessor {
  override def postProcess(openapi: Swagger): VxFuture[Swagger] =
    VxFuture.succeededFuture(openapi.consumes("post-a"))
}

class TestPostBProcessor extends ScalaServiceVerticle with OpenApiPostProcessor {
  override def postProcess(openapi: Swagger): VxFuture[Swagger] =
    VxFuture.succeededFuture(openapi.consumes("post-b"))
}