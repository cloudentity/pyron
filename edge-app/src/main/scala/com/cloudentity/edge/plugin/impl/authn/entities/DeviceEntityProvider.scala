package com.cloudentity.edge.plugin.impl.authn.entities

import com.cloudentity.edge.domain.flow.AuthnCtx
import com.cloudentity.edge.domain.authn.{CloudentityAuthn, CloudentityAuthnCtx}
import com.cloudentity.edge.plugin.impl.authn.EntityProvider
import com.cloudentity.edge.service.DeviceServiceClient
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.TracingContext

import scala.concurrent.Future
import scalaz._

class DeviceEntityProvider extends ScalaServiceVerticle with EntityProvider with CloudentityAuthn {
  var deviceService: DeviceServiceClient = _

  override def vertxServiceAddressPrefixS: Option[String] = Option(verticleId())

  override def initServiceAsyncS(): Future[Unit] =
    DeviceServiceClient.apply(vertx, getConfig)
      .toOperation.map(deviceService = _).run.map(_ => ())

  override def getEntity(tracingCtx: TracingContext, ctx: AuthnCtx): VxFuture[AuthnCtx] = {
    val result = ctx.asCloudentity.deviceUuid match {
      case Some(uuid) => deviceService.getDevice(tracingCtx, uuid).flatMap {
        case \/-(device) => Future.successful(buildCtx(device))
        case -\/(error)  => Future.failed(new Exception(s"Failed to get device: $error"))
      }
      case None       => Future.failed(new Exception("Could not find 'deviceUuid'"))
    }

    result.toJava()
  }

  private def buildCtx(device: DeviceServiceClient.Device): AuthnCtx = {
    CloudentityAuthnCtx(device = Some(device.json)).toCtx
  }
}
