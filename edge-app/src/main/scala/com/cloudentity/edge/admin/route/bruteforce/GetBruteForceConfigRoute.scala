package com.cloudentity.edge.admin.route.bruteforce

import com.cloudentity.edge.admin.service.BruteForce
import com.cloudentity.edge.plugin.impl.bruteforce.BruteForcePlugin
import com.cloudentity.tools.vertx.hazelcast.{HazelcastService, HazelcastServiceClient}
import com.cloudentity.tools.api.errors.ApiError
import com.cloudentity.tools.vertx.server.api.routes.utils.CirceRouteOperations
import com.cloudentity.tools.vertx.server.api.routes.{RouteService, ScalaRouteVerticle}
import io.vertx.ext.web.RoutingContext

import scala.concurrent.Future

class GetBruteForceConfigRoute extends ScalaRouteVerticle with RouteService with CirceRouteOperations {
    var cache: HazelcastServiceClient = _

    override def initService(): Unit =
      cache = HazelcastServiceClient(createClient(classOf[HazelcastService]))

  override protected def handle(ctx: RoutingContext): Unit = {
    val program: Future[ApiError \/ Map[String, List[String]]] =
      BruteForce.getBruteForceListNames(cache).toScala
        .map(names => Map("apiSignatures" -> names.map(_.stripPrefix(BruteForcePlugin.cacheCollectionPrefix)))) // should be 'counterNames', but use 'apiSignatures' for backward compatibility
        .convertError

    handleCompleteS(ctx, OK)(program)
  }
}
