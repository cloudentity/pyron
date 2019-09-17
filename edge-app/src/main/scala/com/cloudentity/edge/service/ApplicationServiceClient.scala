package com.cloudentity.edge.service

import io.circe.parser.{decode, parse}
import io.circe.{Json, ParsingFailure}
import com.cloudentity.edge.domain.Codecs._
import com.cloudentity.edge.commons.ClientWithTracing
import com.cloudentity.edge.jwt.{JwtService, JwtServiceFactory, JwtToken}
import com.cloudentity.edge.service.ApplicationServiceClient._
import com.cloudentity.tools.vertx.http.builder.SmartHttpResponse
import com.cloudentity.tools.vertx.http.{SmartHttp, SmartHttpClient}
import com.cloudentity.tools.vertx.scala.{FutureConversions, Futures}
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import com.cloudentity.tools.vertx.scala.VertxExecutionContext

import scala.concurrent.Future
import scalaz.{-\/, \/, \/-}

object ApplicationServiceClient {
  case class ApplicationServiceConf(applicationService: JsonObject, jwtServiceAddress: String)

  def apply(vertx: Vertx, rawConfig: JsonObject)(implicit ex: VertxExecutionContext): Future[\/[Throwable, ApplicationServiceClient]] = {
    import io.circe.generic.auto._

    decode[ApplicationServiceConf](rawConfig.toString) match {
      case Right(conf) =>
        val jwtService = JwtServiceFactory.createClient(vertx, conf.jwtServiceAddress)
        Futures.toScala(SmartHttp.clientBuilder(vertx, conf.applicationService).build()).map { smartClient =>
          \/-(new ApplicationServiceClient(smartClient, jwtService))
        }
      case Left(error) =>
        Future.failed(new Exception("Failed to decode ApplicationServiceConf: " + error.getMessage))
    }
  }

  case class Application(json: Json)
  sealed trait ApplicationServiceError
  case class ApplicationServiceFailure(code: Int, body: Buffer) extends ApplicationServiceError
  case class ApplicationServiceParsingError(msg: String, err: Throwable) extends ApplicationServiceError
  case class ApplicationServiceHttpError(err: Throwable) extends ApplicationServiceError
}

class ApplicationServiceClient(client: SmartHttpClient, jwtService: JwtService)
                              (implicit ec: VertxExecutionContext) extends FutureConversions with ClientWithTracing {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  def getApplicationByClientId(ctx: TracingContext, oAuthClientId: String): Future[ApplicationServiceError \/ Application] = {
    jwtService.emptySigned.toScala().flatMap { case (jwt, auth) =>
      log.debug(ctx, s"Calling ApplicationService to get application [oAuthClientId=$oAuthClientId, jwt=$jwt]")

      client.get(s"/application/capability/oauthClient/$oAuthClientId")
        .putHeader("Authorization", s"Bearer $auth")
        .withTracing(ctx)
        .endWithBody(ctx).toScala()
        .map(successHandler(ctx, jwt))
        .recover { case ex: Throwable =>
          ctx.logError(ex)
          -\/(ApplicationServiceHttpError(ex))
        }
    }
  }

  def successHandler(ctx: TracingContext, jwt: JwtToken): SmartHttpResponse => ApplicationServiceError \/ Application =
    response => {
      log.debug(ctx, s"Received ${response.getHttp.statusCode()} status code from ApplicationService, jwt=$jwt")
      val body = response.getBody
      response.getHttp.statusCode() match {
        case 200  => parse(body.toString) match {
          case Right(json) =>
            \/-(Application(json))
          case Left(ParsingFailure(msg, error)) =>
            ctx.logError(error)
            -\/(ApplicationServiceParsingError(msg, error))
        }
        case code =>
          ctx.logError(body)
          -\/(ApplicationServiceFailure(code, body))
      }
    }
}
