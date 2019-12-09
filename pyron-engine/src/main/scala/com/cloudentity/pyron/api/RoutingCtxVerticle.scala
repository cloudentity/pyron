package com.cloudentity.pyron.api

import com.cloudentity.pyron.api.ApiHandler.FlowState
import com.cloudentity.pyron.domain.flow.{AccessLogItems}
import io.vertx.ext.web.RoutingContext
import org.slf4j.{Logger, LoggerFactory}

import scala.util.Try

object RoutingCtxData {
  val log: Logger = LoggerFactory.getLogger(this.getClass)

  val correlationSignatureKey = "CORRELATION_SIGNATURE" // we store concatenated flow-id and correlation IDs (from CorrelationIdPlugin) at this key in RoutingContext.data
  val flowStateKey            = "FLOW_STATE"
  val flowIdKey               = "FLOW_ID"

  val propertiesKey           = "_internal.routingContext"

  def updateFlowState(ctx: RoutingContext, f: FlowState => FlowState): Unit =
    ctx.put(flowStateKey, f(getFlowState(ctx)))

  def getFlowState(ctx: RoutingContext): FlowState = {
    Try(Option(ctx.get[FlowState](flowStateKey))) match {
      case scala.util.Success(stateOpt) => stateOpt
      case scala.util.Failure(ex) =>
        log.error("Could not read FlowState from RoutingContext", ex)
        None
    }
  }.getOrElse(FlowState(None, None, None, AccessLogItems()))
}