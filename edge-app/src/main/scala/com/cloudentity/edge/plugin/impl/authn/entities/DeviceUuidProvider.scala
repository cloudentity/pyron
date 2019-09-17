package com.cloudentity.edge.plugin.impl.authn.entities

import com.cloudentity.edge.domain.flow.AuthnCtx
import com.cloudentity.edge.domain.authn.{CloudentityAuthn, CloudentityAuthnCtx}
import com.cloudentity.edge.plugin.impl.authn.EntityProvider
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.Future

class DeviceUuidProvider extends ScalaServiceVerticle with EntityProvider with CloudentityAuthn {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  override def vertxServiceAddressPrefixS: Option[String] = Option(verticleId())

  override def getEntity(tracingCtx: TracingContext, ctx: AuthnCtx): Future[AuthnCtx] =
    ctx.asCloudentity.deviceUuid match {
      case Some(deviceUuid) => Future.succeededFuture(buildAuthnCtx(tracingCtx, deviceUuid))
      case None             => Future.failedFuture(new Exception("Could not find 'deviceUuid'"))
    }

  private def buildAuthnCtx(tracingCtx: TracingContext, deviceUuid: String): AuthnCtx = {
    val ctx = CloudentityAuthnCtx(deviceUuid = Some(deviceUuid)).toCtx
    log.debug(tracingCtx, s"Built AuthnCtx: $ctx")
    ctx
  }
}
