package com.cloudentity.pyron.api

import io.vertx.ext.web.RoutingContext

object AliveHandler {
  def handle(ctx: RoutingContext): Unit = {
    ctx.response().setStatusCode(200).end("Cloudentity Pyron 33")
  }
}

