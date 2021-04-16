package com.cloudentity.pyron.api

import io.vertx.ext.web.RoutingContext
import org.slf4j.{Logger, LoggerFactory}

import scala.util.{Failure, Success, Try}

object RoutingCtxData {
  val log: Logger = LoggerFactory.getLogger(this.getClass)

  val flowStateKey            = "_internal.flowState"
  val propertiesKey           = "_internal.routingContext"

  def updateFlowState(ctx: RoutingContext, f: FlowState => FlowState): Unit =
    ctx.put(flowStateKey, f(getFlowState(ctx)))

  def getFlowState(ctx: RoutingContext): FlowState = {
    Try(Option(ctx.get[FlowState](flowStateKey))) match {
      case Success(stateOpt) => stateOpt
      case Failure(ex) =>
        log.error("Could not read FlowState from RoutingContext", ex)
        None
    }
  }.getOrElse(FlowState.empty)
}