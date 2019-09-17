package com.cloudentity.edge.admin.route

import com.cloudentity.tools.vertx.server.api.routes.RouteVerticle
import io.vertx.ext.web.RoutingContext

class AliveRoute extends RouteVerticle {
  override def handle(ctx: RoutingContext): Unit =
    ctx.response().end(getConfig().getString("msg", "API Gateway Admin"))
}
