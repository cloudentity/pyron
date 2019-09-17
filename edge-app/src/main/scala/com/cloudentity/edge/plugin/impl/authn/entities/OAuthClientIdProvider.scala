package com.cloudentity.edge.plugin.impl.authn.entities

import com.cloudentity.edge.domain.flow.AuthnCtx
import com.cloudentity.edge.domain.authn.{CloudentityAuthn, CloudentityAuthnCtx}
import com.cloudentity.edge.plugin.impl.authn.EntityProvider
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.Future

class OAuthClientIdProvider extends ScalaServiceVerticle with EntityProvider with CloudentityAuthn {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  override def vertxServiceAddressPrefixS: Option[String] = Option(verticleId())

  override def getEntity(tracingCtx: TracingContext, ctx: AuthnCtx) = {
    ctx.asCloudentity.oAuthClientId match {
      case Some(clientId) => Future.succeededFuture(buildAuthnCtx(tracingCtx, clientId))
      case None           => Future.failedFuture(new Exception("Could not find 'oAuthClientId'"))
    }
  }

  private def buildAuthnCtx(tracingCtx: TracingContext, clientId: String): AuthnCtx = {
    val ctx = CloudentityAuthnCtx(oAuthClientId = Option.apply(clientId)).toCtx
    log.debug(tracingCtx, s"Built AuthnCtx: $ctx")
    ctx
  }

}
