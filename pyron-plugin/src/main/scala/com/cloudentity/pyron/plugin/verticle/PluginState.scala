package com.cloudentity.pyron.plugin.verticle

import com.cloudentity.pyron.domain.flow.{PluginName, RequestCtx, ResponseCtx}
import com.cloudentity.tools.vertx.tracing.LoggingWithTracing

trait PluginState[S] {
  def name: PluginName
  def log: LoggingWithTracing
  lazy val stateKey = s"${name.value}PluginState"

    implicit class RequestCtxWithState(ctx: RequestCtx) {
      def withPluginState(state: S): RequestCtx =
          ctx.copy(properties = ctx.properties.updated(stateKey, state))

        def getPluginState(): Option[S] = ctx.properties.get(stateKey)
    }

    implicit class ResponseCtxWithState(ctx: ResponseCtx) {
      def getPluginState(): Option[S] = ctx.properties.get(stateKey)
    }
}