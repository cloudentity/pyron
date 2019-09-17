package com.cloudentity.edge.plugin.impl.authn.entities

import com.cloudentity.edge.domain.flow.AuthnCtx
import com.cloudentity.edge.domain.authn.{CloudentityAuthn, CloudentityAuthnCtx}
import com.cloudentity.edge.plugin.impl.authn.EntityProvider
import com.cloudentity.edge.service.UserServiceClient
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.TracingContext

import scala.concurrent.Future
import scalaz._

class UserEntityProvider extends ScalaServiceVerticle with EntityProvider with CloudentityAuthn {
  var userService: UserServiceClient = _

  override def vertxServiceAddressPrefixS: Option[String] = Option(verticleId())

  override def initServiceAsyncS() =
    UserServiceClient.apply(vertx, getConfig)
      .toOperation.map(userService = _).run.map(_ => ())


  override def getEntity(tracingCtx: TracingContext, ctx: AuthnCtx): VxFuture[AuthnCtx] = {
    val result = ctx.asCloudentity.userUuid match {
        case Some(uuid) => userService.getUser(tracingCtx, uuid).flatMap {
          case \/-(user)  => Future.successful(buildCtx(user))
          case -\/(error) => Future.failed(new Exception(s"Failed to get user: $error"))
        }
        case None       => Future.failed(new Exception("Could not find 'userUuid'"))
    }
    result.toJava()
  }

  protected def buildCtx(user: UserServiceClient.User): AuthnCtx = {
    CloudentityAuthnCtx(user = Some(user.json)).toCtx
  }
}
