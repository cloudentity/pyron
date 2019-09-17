package com.cloudentity.edge.plugin.authn

import java.util.Optional

import io.circe.Json
import com.cloudentity.edge.plugin.bus.request._
import com.cloudentity.edge.domain._
import com.cloudentity.edge.domain.flow.{AuthnCtx, PluginConf, PluginName, RequestCtx}
import com.cloudentity.edge.domain.http.ApiResponse
import com.cloudentity.edge.plugin.RequestPluginService
import com.cloudentity.edge.plugin.impl.authn.{AuthnEdgePlugin, AuthnPluginConf, FlowCtx}
import com.cloudentity.edge.test.TestRequestResponseCtx
import com.cloudentity.tools.vertx.bus.ServiceClientFactory
import com.cloudentity.tools.vertx.http.Headers
import com.cloudentity.tools.vertx.test.ServiceVerticleUnitTest
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.{JsonArray, JsonObject}
import io.vertx.ext.unit.TestContext
import io.vertx.core.{Future => VxFuture}
import org.junit.Test

import scala.concurrent.Future

class AuthnEdgePluginSUT extends AuthnEdgePlugin with RequestPluginService {
  override def callCloudAuthnPlugin(ctx: RequestCtx, conf: AuthnPluginConf): Future[Either[ApiResponse, List[FlowCtx]]] = {
    if (ctx.request.method == HttpMethod.GET)
      Future.successful(Right(List(FlowCtx("userUuid", Json.fromString("xyz")))))
    else Future.successful(Left(ApiResponse(401, Buffer.buffer(), Headers(), None)))
  }
}

class AuthnEdgePluginSpec extends ServiceVerticleUnitTest[AuthnEdgePluginSUT, RequestPluginService] with TestRequestResponseCtx {
  val defaultPluginConf = PluginConf(PluginName("authn"), Json.obj("methods" -> Json.arr()))

  val httpConfig = new JsonObject().put("serviceLocation", new JsonObject().put("host", "").put("port", 80).put("ssl", true))
  val config = new JsonObject()
      .put("http", httpConfig)

  def cacheConfig(ttl: Int, keyHeader: String): JsonObject =
    new JsonObject().put("ttl", ttl).put("keyHeaders", new JsonArray().add(keyHeader))

  @Test
  def shouldReadResponseFromCacheIfKeyInCache(ctx: TestContext): Unit = {
    deployVerticle(config.put("cache", cacheConfig(1000, "Authorization")))
      .compose { _ => // call with GET and expect request with FlowCtx
        client().applyPlugin(TracingContext.dummy(), ApplyRequest(emptyRequestCtx.modifyRequest(_.copy(method = HttpMethod.GET, headers = Headers("Authorization" -> List("abc")))), defaultPluginConf))
      }.compose { response: ApplyResponse =>
        response match {
          case Continue(requestCtx) => ctx.assertEquals(AuthnCtx("userUuid" -> Json.fromString("xyz")), requestCtx.authnCtx)
          case x             => ctx.fail(s"Excepted Continue, got: $x")
        }
        VxFuture.succeededFuture(())
      }.compose { _ => // call with POST and again expect request with FlowCtx (retrieved from cache)
        client().applyPlugin(TracingContext.dummy(), ApplyRequest(emptyRequestCtx.modifyRequest(_.copy(method = HttpMethod.POST, headers = Headers("Authorization" -> List("abc")))), defaultPluginConf))
      }.compose { response: ApplyResponse =>
        response match {
          case Continue(_) => // ok
          case x           => ctx.fail(s"Excepted Continue, got: $x")
        }
        VxFuture.succeededFuture(())
      }
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def shouldNotUseCacheIfTtl0(ctx: TestContext): Unit = {
    deployVerticle(config.put("cache", cacheConfig(0, "Authorization")))
      .compose { _ => // call with GET and expect request with FlowCtx
        client().applyPlugin(TracingContext.dummy(), ApplyRequest(emptyRequestCtx.modifyRequest(_.copy(method = HttpMethod.GET, headers = Headers("Authorization" -> List("abc")))), defaultPluginConf))
      }.compose { response: ApplyResponse =>
      response match {
        case Continue(requestCtx) => ctx.assertEquals(AuthnCtx("userUuid" -> Json.fromString("xyz")), requestCtx.authnCtx)
        case x             => ctx.fail(s"Excepted Continue, got: $x")
      }
      VxFuture.succeededFuture(())
    }.compose { _ => // call with POST and expect response 401
      client().applyPlugin(TracingContext.dummy(), ApplyRequest(emptyRequestCtx.modifyRequest(_.copy(method = HttpMethod.POST, headers = Headers("Authorization" -> List("abc")))), defaultPluginConf))
    }.compose { response: ApplyResponse =>
      response match {
        case Continue(requestCtx) if (requestCtx.isAborted()) => ctx.assertEquals(401, requestCtx.aborted.get.statusCode)
        case x             => ctx.fail(s"Excepted Responde, got: $x")
      }
      VxFuture.succeededFuture(())
    }
      .setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def shouldNotUseCacheIfCacheNotConfigured(ctx: TestContext): Unit = {
    deployVerticle(config)
      .compose { _ => // call with GET and expect request with FlowCtx
        client().applyPlugin(TracingContext.dummy(), ApplyRequest(emptyRequestCtx.modifyRequest(_.copy(method = HttpMethod.GET, headers = Headers("Authorization" -> List("abc")))), defaultPluginConf))
      }.compose { response: ApplyResponse =>
      response match {
        case Continue(requestCtx) => ctx.assertEquals(AuthnCtx("userUuid" -> Json.fromString("xyz")), requestCtx.authnCtx)
        case x             => ctx.fail(s"Excepted Continue, got: $x")
      }
      VxFuture.succeededFuture(())
    }.compose { _ => // call with POST and expect response 401
      client().applyPlugin(TracingContext.dummy(), ApplyRequest(emptyRequestCtx.modifyRequest(_.copy(method = HttpMethod.POST, headers = Headers("Authorization" -> List("abc")))), defaultPluginConf))
    }.compose { response: ApplyResponse =>
      response match {
        case Continue(requestCtx) if (requestCtx.isAborted()) => ctx.assertEquals(401, requestCtx.aborted.get.statusCode)
        case x             => ctx.fail(s"Excepted Responde, got: $x")
      }
      VxFuture.succeededFuture(())
    }
      .setHandler(ctx.asyncAssertSuccess())
  }

  override def createVerticle(): AuthnEdgePluginSUT = new AuthnEdgePluginSUT()

  override def client(): RequestPluginService =
    ServiceClientFactory.make(vertx().eventBus(), classOf[RequestPluginService], Optional.of("authn"))
}
