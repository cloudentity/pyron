package com.cloudentity.edge.admin.route.bruteforce

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import com.cloudentity.edge.admin.service.BruteForce
import com.cloudentity.edge.plugin.impl.bruteforce.BruteForceEvaluator
import com.cloudentity.edge.plugin.impl.bruteforce.BruteForceAttempt
import com.cloudentity.tools.vertx.hazelcast.{HazelcastService, HazelcastServiceClient}
import com.cloudentity.tools.vertx.scala.Operation
import com.cloudentity.tools.api.errors.ApiError
import com.cloudentity.tools.vertx.server.api.routes.utils.CirceRouteOperations
import com.cloudentity.tools.vertx.server.api.routes.{RouteService, ScalaRouteVerticle}
import io.vertx.ext.web.RoutingContext

import scala.concurrent.Future

case class BruteForceResponse(blocked: Boolean, failedAttempts: List[BruteForceAttempt])
object BruteForceResponse {
  implicit val BruteForceResponseDecoder: Decoder[BruteForceResponse] = deriveDecoder
  implicit val BruteForceResponseEncoder: Encoder[BruteForceResponse] = deriveEncoder
}


class GetUserBruteForceAttemptsRoute extends ScalaRouteVerticle with RouteService with CirceRouteOperations with BruteForceEvaluator{
  var cache: HazelcastServiceClient = _

  override def initService(): Unit = {
    cache = HazelcastServiceClient(createClient(classOf[HazelcastService]))
  }


  override protected def handle(ctx: RoutingContext): Unit = {
    val program: Future[ApiError \/ List[BruteForceAttempt]] = {
      for {
        counterName <- getPathParam(ctx, "counterName")
        identifier  <- getPathParam(ctx, "identifier")
        idValue      = BruteForce.extractIdentifierValue(identifier)

        collections <- BruteForce.getBruteForceListNames(cache).toScala.convertError.toOperation
        _           <- validateApiSignature(counterName, collections)

        attempts    <- BruteForce.getBruteForceAttempts(cache)(counterName, idValue).toOperation
        attemptsResult <- failIfNotExist(attempts)
      } yield (attemptsResult)
    }.run

    handleCompleteS(ctx, OK)(program.map(_.map(attempts => BruteForceResponse(isBlocked(attempts), attempts))))
  }

  def failIfNotExist(attempts: Option[List[BruteForceAttempt]]): Operation[ApiError, List[BruteForceAttempt]] =
    attempts.toOperation(notFoundError)

}