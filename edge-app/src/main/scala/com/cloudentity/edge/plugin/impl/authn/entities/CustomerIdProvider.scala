package com.cloudentity.edge.plugin.impl.authn.entities

import com.cloudentity.edge.domain.flow.AuthnCtx
import com.cloudentity.edge.domain.authn.{CloudentityAuthn, CloudentityAuthnCtx}
import com.cloudentity.edge.plugin.impl.authn.EntityProvider
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.Future

class CustomerIdProvider extends ScalaServiceVerticle with EntityProvider with CloudentityAuthn {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  override def vertxServiceAddressPrefixS: Option[String] = Option(verticleId())

  override def getEntity(tracingCtx: TracingContext, ctx: AuthnCtx) = {
    ctx.asCloudentity.customerId match {
      case Some(cid) => Future.succeededFuture(buildAuthnCtx(tracingCtx, cid))
      case None      => Future.failedFuture(new Exception("Could not find customer id"))
    }
  }

  private def buildAuthnCtx(tracingCtx: TracingContext, cid: String): AuthnCtx = {
    val ctx = CloudentityAuthnCtx(customerId = Some(cid)).toCtx
    log.debug(tracingCtx, s"Built AuthnCtx: $ctx")
    ctx
  }
}