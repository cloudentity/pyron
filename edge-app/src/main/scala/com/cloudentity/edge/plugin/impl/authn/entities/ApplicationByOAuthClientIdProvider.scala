package com.cloudentity.edge.plugin.impl.authn.entities

import com.cloudentity.edge.domain.flow.AuthnCtx
import com.cloudentity.edge.domain.authn.{CloudentityAuthn, CloudentityAuthnCtx}
import com.cloudentity.edge.plugin.impl.authn.EntityProvider
import com.cloudentity.edge.service.ApplicationServiceClient
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.TracingContext

import scala.concurrent.Future
import scalaz._

class ApplicationByOAuthClientIdProvider extends ScalaServiceVerticle with EntityProvider with CloudentityAuthn {
  var applicationService: ApplicationServiceClient = _

  override def vertxServiceAddressPrefixS: Option[String] = Option(verticleId())

  override def initServiceAsyncS(): Future[Unit] =
    ApplicationServiceClient.apply(vertx, getConfig)
      .toOperation.map(applicationService = _).run.map(_ => ())

  override def getEntity(tracingCtx: TracingContext, ctx: AuthnCtx): VxFuture[AuthnCtx] = {
    val result = ctx.asCloudentity.oAuthClientId match {
      case Some(clientId) => applicationService.getApplicationByClientId(tracingCtx, clientId).flatMap {
        case \/-(device)  => Future.successful(buildCtx(device))
        case -\/(error)   => Future.failed(new Exception(s"Failed to get application: $error"))
      }
      case None => Future.failed(new Exception("Could not find 'oAuthClientId'"))
    }

    result.toJava()
  }

  protected def buildCtx(application: ApplicationServiceClient.Application): AuthnCtx = {
    CloudentityAuthnCtx(application = Some(application.json)).toCtx
  }
}

class ApplicationUuidByOAuthClientIdProvider extends ApplicationByOAuthClientIdProvider {
  override protected def buildCtx(application: ApplicationServiceClient.Application): AuthnCtx = {
    val applicationUuidOpt = application.json.asObject.flatMap(_.apply("id")).flatMap(_.asString)
    CloudentityAuthnCtx(applicationUuid = applicationUuidOpt).toCtx
  }
}
