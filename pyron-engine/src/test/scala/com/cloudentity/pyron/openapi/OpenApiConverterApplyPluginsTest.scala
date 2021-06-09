package com.cloudentity.pyron.openapi

import com.cloudentity.pyron.domain.flow._
import com.cloudentity.pyron.domain.openapi.OpenApiRule
import com.cloudentity.pyron.plugin.RequestPluginService
import com.cloudentity.pyron.plugin.config.ValidateResponse
import com.cloudentity.pyron.plugin.openapi._
import com.cloudentity.pyron.plugin.verticle.RequestPluginVerticle
import com.cloudentity.pyron.util.OpenApiTestUtils
import com.cloudentity.tools.vertx.conf.fixed.FixedConfVerticle
import com.cloudentity.tools.vertx.test.ScalaVertxUnitTest
import com.cloudentity.tools.vertx.tracing.TracingContext
import com.cloudentity.tools.vertx.verticles.VertxDeploy
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
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

  override def validate(conf: Unit): ValidateResponse = throw new NotImplementedError

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
    val plugins = List(ApiGroupPluginConf(PluginName("dummy"), pluginConf, None))
    val rules = List(OpenApiRule(
      method = HttpMethod.POST,
      serviceId = sampleServiceId,
      group = GroupMatchCriteria.empty,
      pathPattern = PathPattern("/test"),
      pathPrefix = PathPrefix(""),
      dropPathPrefix = false,
      rewriteMethod = None,
      rewritePath = None,
      plugins = plugins,
      tags = Nil,
      operationId = None
    ))

    FixedConfVerticle.deploy(vertx, new JsonObject())
      .compose(_ => VertxDeploy.deploy(vertx, new ChangeBasePathDummyPlugin))
      .compose(_ => VertxDeploy.deploy(vertx, verticle))
      .compose(_ => verticle.applyPlugins(TracingContext.dummy(), swagger, rules).compose { res =>
        ctx.assertEquals("/test", res.getBasePath)
        VxFuture.succeededFuture(())
      })
      .onComplete(ctx.asyncAssertSuccess())

    ctx.assertTrue(true)
  }

}
