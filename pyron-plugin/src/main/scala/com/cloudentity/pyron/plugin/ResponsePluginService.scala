package com.cloudentity.pyron.plugin

import com.cloudentity.pyron.plugin.bus.response._
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.vertx.core.Future

trait ResponsePluginService extends ValidatePluginService with ConvertOpenApiService {
  @VertxEndpoint(address = ":response-plugin.apply")
  def applyPlugin(ctx: TracingContext, req: ApplyRequest): Future[ApplyResponse]
}
