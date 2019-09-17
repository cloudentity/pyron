package com.cloudentity.edge.admin.route.bruteforce

import com.cloudentity.edge.admin.service.BruteForce
import com.cloudentity.edge.plugin.impl.bruteforce.BruteForceEvaluator
import com.cloudentity.tools.vertx.hazelcast.{HazelcastService, HazelcastServiceClient}
import com.cloudentity.tools.api.errors.ApiError
import com.cloudentity.tools.vertx.server.api.routes.utils.CirceRouteOperations
import com.cloudentity.tools.vertx.server.api.routes.{RouteService, ScalaRouteVerticle}
import io.vertx.ext.web.RoutingContext

import scala.concurrent.Future

class DeleteUserBruteForceAttemptsRoute extends ScalaRouteVerticle with RouteService with CirceRouteOperations with BruteForceEvaluator {

  var cache: HazelcastServiceClient = _

  override def initService(): Unit = {
    cache = HazelcastServiceClient(createClient(classOf[HazelcastService]))
  }

  override protected def handle(ctx: RoutingContext): Unit = {
    val program: Future[ApiError \/ Unit] = {
      for {
        counterName <- getPathParam(ctx, "counterName")
        identifier  <- getPathParam(ctx, "identifier")
        idValue      = BruteForce.extractIdentifierValue(identifier)

        collections <- BruteForce.getBruteForceListNames(cache).toScala.convertError.toOperation
        _           <- validateApiSignature(counterName, collections)

        _           <- BruteForce.clearUserBruteForceAttempts(cache)(counterName, idValue).toOperation
      } yield ()
    }.run

    handleCompleteNoBodyS(ctx, NoContent)(program)
  }

}