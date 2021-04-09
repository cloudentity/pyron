package com.cloudentity.pyron.api

import com.cloudentity.pyron.domain.flow.{FlowFailure, _}
import com.cloudentity.pyron.domain.rule._
import com.cloudentity.pyron.rule.Rule
import com.cloudentity.tools.vertx.bus.VertxEndpoint
import io.vertx.core.{Future => VxFuture}
import io.vertx.ext.web.RoutingContext

trait ApiHandler {
  @VertxEndpoint
  def handle(defaultRequestBodyMaxSize: Option[Kilobytes], ctx: RoutingContext): VxFuture[Unit]
}

object ApiHandler {
  sealed trait ApiError
  case object NoRuleError extends ApiError
  case class RequestPluginError(err: Throwable) extends ApiError
  case class ResponsePluginError(err: Throwable) extends ApiError

  case class FlowState(authnCtx: Option[AuthnCtx],
                       rule: Option[Rule],
                       aborted: Option[Boolean],
                       failure: Option[FlowFailure],
                       extraAccessLogs: AccessLogItems,
                       properties: Properties)

}



