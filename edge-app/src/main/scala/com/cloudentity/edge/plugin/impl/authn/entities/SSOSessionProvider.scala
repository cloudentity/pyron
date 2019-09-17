package com.cloudentity.edge.plugin.impl.authn.entities

import io.circe.Json
import com.cloudentity.edge.domain.flow.AuthnCtx
import com.cloudentity.edge.domain.authn.{CloudentityAuthn, CloudentityAuthnCtx}
import com.cloudentity.edge.plugin.impl.authn.EntityProvider
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.Future

class SSOSessionProvider extends ScalaServiceVerticle with EntityProvider with CloudentityAuthn {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  override def vertxServiceAddressPrefixS: Option[String] = Option(verticleId())

  override def getEntity(tracingCtx: TracingContext, ctx: AuthnCtx) = {
    ctx.asCloudentity.session match {
      case Some(session) => Future.succeededFuture(buildAuthnCtx(tracingCtx, session))
      case None          => Future.failedFuture(new Exception("Could not find 'session'"))
    }
  }

  private def buildAuthnCtx(tracingCtx: TracingContext, session: Json): AuthnCtx = {
    val ctx = CloudentityAuthnCtx(session = Some(session)).toCtx
    log.debug(tracingCtx, s"Built AuthnCtx: $ctx")
    ctx
  }
}
