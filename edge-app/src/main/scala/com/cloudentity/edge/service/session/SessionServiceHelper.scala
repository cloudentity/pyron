package com.cloudentity.edge.service.session

import com.cloudentity.edge.domain.http.TargetRequest
import com.cloudentity.edge.jwt.{JwtService, JwtToken}
import com.cloudentity.edge.service.session.SessionServiceClient._
import com.cloudentity.tools.vertx.scala.FutureConversions
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import scalaz.{-\/, \/-}

import scala.concurrent.Future

trait SessionServiceHelper extends FutureConversions {
  def log: LoggingWithTracing

  def client: SessionServiceClient

  def getSession(ctx: TracingContext, req: TargetRequest, tokenOpt: Option[OrchisToken], jwtService: JwtService, tokenName: String)
                (implicit ec: VertxExecutionContext): Future[Option[Session]] =
    tokenOpt match {
      case Some(token) =>
        for {
          jwtToken <- buildJwtToken(token, tokenName, jwtService)
          response <- client.getSession(ctx, tokenName, token, jwtToken)
        } yield response match {
          case -\/(error)   => logError(ctx, error, req); None
          case \/-(session) => Some(session)
        }
      case None =>
        Future.successful(None)
    }

  def buildJwtToken(token: OrchisToken, tokenName: String, jwtService: JwtService)
                   (implicit ec: VertxExecutionContext): Future[JwtToken] =
    jwtService.empty().toScala().map(_.put(tokenName, token.value))

  def findToken(req: TargetRequest, sessionTokenName: String): Option[OrchisToken] = {
    req.headers.get(sessionTokenName).map(OrchisToken.apply)
  }

  def logError(ctx: TracingContext, error: SessionServiceError, req: TargetRequest): Unit = error match {
    case SessionServiceFailure(status, body) =>
      log.debug(ctx, s"Failed to call Session Service status='$status', body='$body' for request='${req.toString}'")

    case SessionServiceParsingError(message, ex) =>
      log.error(ctx, s"Error on parsing Session Service response, message='$message', request='${req.toString}'", ex)

    case SessionServiceHttpError(message, ex) =>
      log.error(ctx, s"Error on making HTTP call to Session Service, message='$message', request='${req.toString}'", ex)
  }
}

case class OrchisToken(value: String) extends AnyVal {
  def sample: String = value.take(4) // using token prefix for logging purpose
}
