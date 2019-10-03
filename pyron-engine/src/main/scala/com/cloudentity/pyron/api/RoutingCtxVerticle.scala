package com.cloudentity.pyron.api

import com.cloudentity.pyron.api.ApiHandler.FlowState
import com.cloudentity.pyron.domain.flow.{AccessLogItems, CorrelationCtx, FlowId}
import com.cloudentity.tools.vertx.bus.{ServiceClientFactory, VertxEndpoint}
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.vertx.core.{Vertx, Future => VxFuture}
import io.vertx.ext.web.RoutingContext
import org.slf4j.{Logger, LoggerFactory}

import scala.util.Try

object RoutingCtxHandler {
  def handle(vertx: Vertx, client: RoutingCtxService)(ctx: RoutingContext): Unit = {
    val flowId = RoutingCtxData.getFlowId(ctx)
    client.set(flowId, ctx).setHandler(_ => ctx.next())
  }
}

object RoutingCtxData {
  val log: Logger = LoggerFactory.getLogger(this.getClass)

  val correlationSignatureKey = "CORRELATION_SIGNATURE" // we store concatenated flow-id and correlation IDs (from CorrelationIdPlugin) at this key in RoutingContext.data
  val flowStateKey            = "FLOW_STATE"
  val flowIdKey               = "FLOW_ID"

  def updateFlowState(ctx: RoutingContext, f: FlowState => FlowState): Unit =
    ctx.put(flowStateKey, f(getFlowState(ctx)))

  def setFlowId(ctx: RoutingContext, flowId: FlowId): Unit =
    ctx.put(flowIdKey, flowId)

  /**
    * WARNING: Returns null if called before CorrelationIdHandler has been executed.
    */
  def getFlowId(ctx: RoutingContext): FlowId =
    ctx.get[FlowId](flowIdKey)

  def setCorrelationSignature(ctx: RoutingContext, signature: String): Unit =
    ctx.put(correlationSignatureKey, signature)

  /**
    * WARNING: Returns null if called before CorrelationIdHandler has been executed.
    */
  def getCorrelationSignature(ctx: RoutingContext): String =
    ctx.get[String](correlationSignatureKey)

  def getFlowState(ctx: RoutingContext): FlowState = {
    Try(Option(ctx.get[FlowState](flowStateKey))) match {
      case scala.util.Success(stateOpt) => stateOpt
      case scala.util.Failure(ex) =>
        log.error("Could not read FlowState from RoutingContext", ex)
        None
    }
  }.getOrElse(FlowState(None, None, None, AccessLogItems()))
}

trait RoutingCtxService {
  @VertxEndpoint def update(flowId: FlowId, f: RoutingContext => Unit): VxFuture[Unit]
  @VertxEndpoint def remove(flowId: FlowId): VxFuture[Unit]
  @VertxEndpoint def set(flowId: FlowId, ctx: RoutingContext): VxFuture[Unit]
}

class RoutingCtxVerticle() extends ScalaServiceVerticle with RoutingCtxService {
  val ctxMap: collection.mutable.Map[FlowId, RoutingContext] = collection.mutable.Map()

  override def update(flowId: FlowId, f: RoutingContext => Unit): VxFuture[Unit] =
    ctxMap.get(flowId) match {
      case Some(ctx) =>
        f(ctx)
        VxFuture.succeededFuture(())
      case None =>
        VxFuture.failedFuture("Could not find RoutingContext")
    }

  override def set(flowId: FlowId, ctx: RoutingContext): VxFuture[Unit] = {
    ctxMap.put(flowId, ctx)
    VxFuture.succeededFuture(())
  }

  override def remove(flowId: FlowId): VxFuture[Unit] = {
    ctxMap.remove(flowId)
    VxFuture.succeededFuture(())
  }

}
