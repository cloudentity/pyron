package com.cloudentity.edge.openapi

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import com.cloudentity.edge.domain.flow.{GroupMatchCriteria, PathPattern, PathPrefix, PluginConf, PluginName, RequestCtx}
import com.cloudentity.edge.domain.openapi.OpenApiRule
import com.cloudentity.edge.plugin.RequestPluginService
import com.cloudentity.edge.plugin.openapi._
import com.cloudentity.edge.plugin.verticle.RequestPluginVerticle
import com.cloudentity.edge.util.OpenApiTestUtils
import com.cloudentity.tools.vertx.conf.fixed.FixedConfVerticle
import com.cloudentity.tools.vertx.test.ScalaVertxUnitTest
import com.cloudentity.tools.vertx.tracing.TracingContext
import com.cloudentity.tools.vertx.verticles.VertxDeploy
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.core.{Future => VxFuture}
import io.vertx.ext.unit.TestContext
import org.junit.Test

import scala.concurrent.Future

class ChangeBasePathDummyPlugin extends RequestPluginVerticle[Unit] with RequestPluginService {
  override def name: PluginName = PluginName("dummy")
  override def confDecoder: Decoder[Unit] = deriveDecoder

  override def apply(requestCtx: RequestCtx, conf: Unit): Future[RequestCtx] = Future.successful(requestCtx)

  override def validate(conf: Unit) = ???

  override def convertOpenApi(ctx: TracingContext, req: ConvertOpenApiRequest): VxFuture[ConvertOpenApiResponse] = {
    VxFuture.succeededFuture(ConvertedOpenApi(req.swagger.basePath("/test")))
  }
}

class OpenApiConverterApplyPluginsTest extends ScalaVertxUnitTest with OpenApiTestUtils {

  @Test
  def applyDummyPlugin(ctx: TestContext): Unit = {
    val verticle = new OpenApiConverterVerticle()
    val swagger = sampleSwagger("/", Map())
    val pluginConf = io.circe.Json.fromString("")
    val plugins = List(PluginConf(PluginName("dummy"), pluginConf))
    val rules = List(OpenApiRule(HttpMethod.POST, sampleServiceId, GroupMatchCriteria.empty, PathPattern("/test"), PathPrefix(""), false, None, None, plugins, Nil))

    FixedConfVerticle.deploy(vertx, new JsonObject())
      .compose(_ => VertxDeploy.deploy(vertx, new ChangeBasePathDummyPlugin))
      .compose(_ => VertxDeploy.deploy(vertx, verticle))
      .compose(_ => verticle.applyPlugins(TracingContext.dummy(), swagger, rules).compose { res =>
        ctx.assertEquals("/test", res.getBasePath)
        VxFuture.succeededFuture(())
      })
      .setHandler(ctx.asyncAssertSuccess())

    ctx.assertTrue(true)
  }

}
