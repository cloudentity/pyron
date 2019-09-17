package com.cloudentity.edge.plugin.impl.authn.entities

import com.cloudentity.edge.domain.flow.AuthnCtx
import com.cloudentity.edge.domain.authn.{CloudentityAuthn, CloudentityAuthnCtx}
import com.cloudentity.edge.plugin.impl.authn.EntityProvider
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.Future

class RealmProvider extends ScalaServiceVerticle with EntityProvider with CloudentityAuthn {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  override def vertxServiceAddressPrefixS: Option[String] = Option(verticleId())

  override def getEntity(tracingCtx: TracingContext, ctx: AuthnCtx) = {
    ctx.asCloudentity.realm match {
      case Some(realm) => Future.succeededFuture(buildAuthnCtx(tracingCtx, realm))
      case None        => Future.failedFuture(new Exception("Could not find realm"))
    }
  }

  private def buildAuthnCtx(tracingCtx: TracingContext, realm: String): AuthnCtx = {
    val ctx = CloudentityAuthnCtx(realm = Some(realm)).toCtx
    log.debug(tracingCtx, s"Built AuthnCtx: $ctx")
    ctx
  }
}