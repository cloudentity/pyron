package com.cloudentity.edge.plugin.impl.authn.entities

import com.cloudentity.edge.domain.authn.CloudentityAuthnCtx
import com.cloudentity.edge.domain.flow.AuthnCtx
import com.cloudentity.edge.plugin.impl.authn.EntityProvider
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.Future

class JWTContentProvider extends ScalaServiceVerticle with EntityProvider {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  override def vertxServiceAddressPrefixS: Option[String] = Option(verticleId())

  override def getEntity(tracingCtx: TracingContext, ctx: AuthnCtx) = {
    Future.succeededFuture(buildAuthnCtx(tracingCtx, ctx))
  }

  private def buildAuthnCtx(tracingCtx: TracingContext, internalCtx: AuthnCtx): AuthnCtx = {
    val ctx = CloudentityAuthnCtx(custom = Option.apply(internalCtx.value)).toCtx
    log.debug(tracingCtx, s"Built AuthnCtx: $ctx")
    ctx
  }

}
