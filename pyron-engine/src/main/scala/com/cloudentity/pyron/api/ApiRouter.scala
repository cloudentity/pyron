package com.cloudentity.pyron.api

import java.util.Optional

import com.cloudentity.pyron.accesslog.AccessLogHandler
import com.cloudentity.pyron.openapi.route._
import com.cloudentity.pyron.config.Conf.{AppConf, OpenApiConf}
import com.cloudentity.pyron.openapi.route.GetOpenApiRoute
import com.cloudentity.tools.vertx.bus.ServiceClientFactory
import com.cloudentity.tools.vertx.scala.FutureConversions
import com.cloudentity.tools.vertx.server.api.routes.RouteService
import com.cloudentity.tools.vertx.tracing.TracingManager
import io.vertx.core
import io.vertx.core.http.HttpMethod
import io.vertx.core.Vertx
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.{BodyHandler, StaticHandler}
import org.slf4j.LoggerFactory

object ApiRouter extends FutureConversions {
  val log = LoggerFactory.getLogger(this.getClass)

  def createRouter(vertx: Vertx, conf: AppConf, tracing: TracingManager): Router = {
    val apiHandler        = ServiceClientFactory.make(vertx.eventBus(), classOf[ApiHandler])
    val routingCtxService = ServiceClientFactory.make(vertx.eventBus(), classOf[RoutingCtxService])

    val router     = Router.router(vertx)

    router.route().handler(ProxyHeadersHandler.handle(conf.proxyHeaders))
    router.route().handler(AccessLogHandler.createHandler(vertx, tracing, conf.accessLog))
    router.route.handler(BodyHandler.create().setMergeFormAttributes(false).handle)
    router.route().handler(CorrelationIdHandler.handle(tracing))
    router.route(conf.alivePath.getOrElse("/alive")).handler(AliveHandler.handle)

    handleOpenApis(conf.openApi, tracing, vertx, router)

    if (conf.docsEnabled.getOrElse(false)) {
      router.route("/docs/*").handler(StaticHandler.create())
    }

    router.route().handler(RoutingCtxHandler.handle(vertx, routingCtxService))
    router.route("/*").handler(apiHandler.handle)
    router.exceptionHandler { ex => log.error("Unhandled exception", ex) }
    router
  }

  private def handleOpenApis(openApiConf: Option[OpenApiConf], tracing: TracingManager, vertx: core.Vertx, router: Router) = {
    if (openApiConf.flatMap(_.enabled).getOrElse(true)) {
      val basePath = openApiConf.flatMap(_.basePath).getOrElse("/openapi")
      val endpointPath = basePath + "/:serviceName"

      val getTimeout = openApiConf.flatMap(_.getTimeout).getOrElse(30000)
      val getOpenApiClient = ServiceClientFactory.makeWithTracing(vertx.eventBus(), tracing, classOf[RouteService], Optional.of(GetOpenApiRoute.verticleId), new DeliveryOptions().setSendTimeout(getTimeout))
      router.route(HttpMethod.GET, endpointPath).handler(ctx =>
        getOpenApiClient.handleRequest(ctx)
      )

      val listTimeout = openApiConf.flatMap(_.listTimeout).getOrElse(30000)
      val listOpenApisClient = ServiceClientFactory.makeWithTracing(vertx.eventBus(), tracing, classOf[RouteService], Optional.of(ListOpenApiRoute.verticleId), new DeliveryOptions().setSendTimeout(getTimeout))
      router.route(HttpMethod.GET, basePath).handler(ctx =>
        listOpenApisClient.handleRequest(ctx)
      )
    }
  }
}
