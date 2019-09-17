package com.cloudentity.edge.plugin.impl.authn.entities

import io.circe.Json
import com.cloudentity.edge.domain.authn.CloudentityAuthnCtx
import com.cloudentity.edge.domain.flow.AuthnCtx
import com.cloudentity.edge.plugin.impl.authn.EntityProvider
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.Future

class UserStatusProvider extends ScalaServiceVerticle with EntityProvider {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  override def vertxServiceAddressPrefixS: Option[String] = Option(verticleId())

  override def getEntity(tracingCtx: TracingContext, ctx: AuthnCtx) = {
    ctx.get("status") match {
      case Some(status) => Future.succeededFuture(buildAuthnCtx(tracingCtx, status))
      case None        => Future.failedFuture(new Exception("Could not find 'status'"))
    }
  }

  private def buildAuthnCtx(tracingCtx: TracingContext, status: Json): AuthnCtx = {
    val ctx = CloudentityAuthnCtx(custom = Option.apply(Map("status" -> status))).toCtx
    log.debug(tracingCtx, s"Built AuthnCtx: $ctx")
    ctx
  }

}
