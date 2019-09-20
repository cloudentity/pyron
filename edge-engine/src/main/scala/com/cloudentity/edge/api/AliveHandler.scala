package com.cloudentity.edge.api

import io.vertx.ext.web.RoutingContext

object AliveHandler {
  def handle(ctx: RoutingContext): Unit = {
    ctx.response().setStatusCode(200).end("Cloudentity Edge")
  }
}

