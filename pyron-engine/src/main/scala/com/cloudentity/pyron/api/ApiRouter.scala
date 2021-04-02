package com.cloudentity.pyron.api

import java.util.Optional
import com.cloudentity.pyron.accesslog.AccessLogHandler
import com.cloudentity.pyron.openapi.route._
import com.cloudentity.pyron.config.Conf.{AppConf, OpenApiConf}
import com.cloudentity.pyron.openapi.route.GetOpenApiRoute
import com.cloudentity.tools.vertx.bus.VertxEndpointClient
import com.cloudentity.tools.vertx.scala.FutureConversions
import com.cloudentity.tools.vertx.server.api.routes.RouteService
import com.cloudentity.tools.vertx.tracing.TracingManager
import io.vertx.core
import io.vertx.core.http.HttpMethod
import io.vertx.core.Vertx
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.StaticHandler
import org.slf4j.{Logger, LoggerFactory}

object ApiRouter extends FutureConversions {
  val log: Logger = LoggerFactory.getLogger(this.getClass)

  def createRouter(vertx: Vertx, conf: AppConf, tracing: TracingManager): Router = {
    val router = Router.router(vertx)
    router.route().handler(ProxyHeadersHandler.handle(conf.proxyHeaders) _)
    router.route().handler(AccessLogHandler.createHandler(vertx, tracing, conf.accessLog))
    router.route(conf.alivePath.getOrElse("/alive")).handler(AliveHandler.handle)

    handleOpenApis(conf.openApi, tracing, vertx, router)

    if (conf.docsEnabled.getOrElse(false)) {
      router.route("/docs/*").handler(StaticHandler.create())
    }

    val apiHandler = VertxEndpointClient.make(vertx, classOf[ApiHandler])
    router.route("/*").handler { ctx =>
      ctx.request().pause()
      apiHandler.handle(conf.defaultRequestBodyMaxSize, ctx)
    }

    router.errorHandler(500, ex => log.error("Unhandled exception", ex))
    router
  }

  private def handleOpenApis(openApiConf: Option[OpenApiConf], tracing: TracingManager, vertx: core.Vertx, router: Router) = {
    if (openApiConf.flatMap(_.enabled).getOrElse(true)) {
      val basePath = openApiConf.flatMap(_.basePath).getOrElse("/openapi")
      val endpointPath = basePath + "/:serviceName"
      val getTimeout = openApiConf.flatMap(_.getTimeout).getOrElse(30000)
      router.route(HttpMethod.GET, endpointPath).handler(ctx =>
        getOpenApiClient(tracing, vertx, getTimeout).handleRequest(ctx)
      )
      router.route(HttpMethod.GET, basePath).handler(ctx => listOpenApisClient(tracing, vertx, getTimeout).handleRequest(ctx))
    }
  }

  private def getOpenApiClient(tracing: TracingManager, vertx: Vertx, getTimeout: Int): RouteService = {
    VertxEndpointClient.makeWithTracing(
      vertx,
      tracing,
      classOf[RouteService],
      Optional.of(GetOpenApiRoute.verticleId),
      new DeliveryOptions().setSendTimeout(getTimeout)
    )
  }

  private def listOpenApisClient(tracing: TracingManager, vertx: Vertx, getTimeout: Int): RouteService = {
    VertxEndpointClient.makeWithTracing(
      vertx,
      tracing,
      classOf[RouteService],
      Optional.of(ListOpenApiRoute.verticleId),
      new DeliveryOptions().setSendTimeout(getTimeout)
    )
  }

}
