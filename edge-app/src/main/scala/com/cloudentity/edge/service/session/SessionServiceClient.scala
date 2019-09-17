package com.cloudentity.edge.service.session

import io.circe.parser._
import io.circe.{Decoder, JsonObject}
import com.cloudentity.edge.jwt.{JwtService, JwtToken}
import com.cloudentity.edge.plugin.impl.session.SessionServiceConf
import com.cloudentity.edge.service.session.SessionServiceClient._
import com.cloudentity.tools.vertx.scala.Futures
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import io.vertx.core.Vertx
import io.vertx.core.http._
import scalaz.{-\/, \/, \/-}

import scala.concurrent.{Future, Promise}

object SessionServiceClient {
  type Session = JsonObject
  type TokenName = String

  sealed trait SessionServiceError
  case class SessionServiceFailure(status: Int, body: Buffer) extends SessionServiceError
  case class SessionServiceHttpError(message: String, error: Throwable) extends SessionServiceError
  case class SessionServiceParsingError(message: String, error: Throwable) extends SessionServiceError

  def apply(vertx: Vertx, config: SessionServiceConf, jwtService: JwtService)
           (implicit ec: VertxExecutionContext): SessionServiceClient =
    new SessionServiceClient(vertx.createHttpClient(options(config)), jwtService, config)

  def options(config: SessionServiceConf): HttpClientOptions = new HttpClientOptions()
    .setDefaultHost(config.host)
    .setDefaultPort(config.port)
    .setSsl(config.ssl)
    .setIdleTimeout(config.timeout)
    .setConnectTimeout(config.timeout)
    .setLogActivity(config.debug)
}

class SessionServiceClient(client: HttpClient, jwtService: JwtService, config: SessionServiceConf)
                          (implicit val ec: VertxExecutionContext) {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  def getSession(ctx: TracingContext, tokenName: TokenName, token: OrchisToken, jwt: JwtToken): Future[SessionServiceError \/ Session] =
    call[Session](ctx, jwt) {
      log.debug(ctx, s"Calling Session Service to get session for ${token.sample} as $tokenName")
      val request = client.get(s"${config.path}/hmac/session")
      request.putHeader(tokenName, token.value)
    }

  private def call[A](ctx: TracingContext, token: JwtToken)(request: HttpClientRequest)(implicit d: Decoder[A]): Future[SessionServiceError \/ A] = {
    Futures.toScala(jwtService.sign(token)).flatMap { auth =>
      val promise = Promise[SessionServiceError \/ A]()
      val builder = request.putHeader("Authorization", s"Bearer $auth")
      ctx.consumeContext((k, v) => builder.putHeader(k, v))
      builder.handler(successHandler[A](promise))
        .exceptionHandler(ex => promise.success(-\/(SessionServiceHttpError(ex.getMessage, ex))))
        .end()
      promise.future
    }
  }

  private def successHandler[A](p: Promise[SessionServiceError \/ A])(implicit d: Decoder[A]): Handler[HttpClientResponse] =
    response => {
      response.statusCode() match {
        case x if x >= 200 && x < 300 => response.bodyHandler(b => p.success(decode(b, d)))
        case e => response.bodyHandler(b => p.success(-\/(SessionServiceFailure(e, b))))
      }
  }

  private def decode[A](buffer: Buffer, d: Decoder[A]): SessionServiceError \/ A =
    parse(buffer.toString("UTF-8")) match {
      case Left(failure) => -\/(SessionServiceParsingError(failure.message, failure.underlying))
      case Right(json)   => d.decodeJson(json) match {
        case Left(failure) => -\/(SessionServiceParsingError(failure.message, failure.getCause))
        case Right(a)      => \/-(a)
      }
    }
}
