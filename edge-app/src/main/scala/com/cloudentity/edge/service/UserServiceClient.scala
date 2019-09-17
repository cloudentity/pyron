package com.cloudentity.edge.service

import io.circe.parser._
import io.circe.{Json, ParsingFailure}
import com.cloudentity.edge.commons.ClientWithTracing
import com.cloudentity.edge.domain.Codecs._
import com.cloudentity.edge.jwt.{JwtService, JwtServiceFactory, JwtToken}
import com.cloudentity.edge.service.UserServiceClient._
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

object UserServiceClient {
  case class UserServiceConf(userService: JsonObject, jwtServiceAddress: String)

  def apply(vertx: Vertx, rawConfig: JsonObject)(implicit ex: VertxExecutionContext): Future[\/[Throwable, UserServiceClient]] = {
    import io.circe.generic.auto._

    decode[UserServiceConf](rawConfig.toString) match {
      case Right(conf) =>
        val jwtService = JwtServiceFactory.createClient(vertx, conf.jwtServiceAddress)
        Futures.toScala(SmartHttp.clientBuilder(vertx, conf.userService).build()).map { smartClient =>
          \/-(new UserServiceClient(smartClient, jwtService))
        }
      case Left(error) =>
        Future.failed(new Exception("Failed to decode UserServiceConf: " + error.getMessage))
    }
  }

  case class User(json: Json)
  sealed trait UserServiceError
    case class UserServiceFailure(code: Int, body: Buffer) extends UserServiceError
    case class UserServiceParsingError(msg: String, err: Throwable) extends UserServiceError
    case class UserServiceHttpError(err: Throwable) extends UserServiceError
}

class UserServiceClient(client: SmartHttpClient, jwtService: JwtService)
                       (implicit ec: VertxExecutionContext) extends FutureConversions with ClientWithTracing {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  def getUser(ctx: TracingContext, uuid: String): Future[UserServiceError \/ User] = {
    jwtService.emptySigned.toScala().flatMap { case (jwt, auth) =>
      log.debug(ctx, s"Calling UserService to get user [uuid=$uuid, jwt=$jwt]")

      client.get(s"/hmac/users/$uuid")
        .putHeader("Authorization", s"Bearer $auth")
        .withTracing(ctx)
        .endWithBody(ctx).toScala()
        .map(successHandler(ctx, jwt))
        .recover { case ex: Throwable => -\/(UserServiceHttpError(ex)) }
    }
  }

  def successHandler(ctx: TracingContext, jwt: JwtToken): SmartHttpResponse => UserServiceError \/ User = {
    response => {
      log.debug(ctx, s"Received ${response.getHttp.statusCode()} status code from UserService, jwt=$jwt")
      val body = response.getBody
      response.getHttp.statusCode() match {
        case 200  => parse(body.toString) match {
          case Right(json)                      => \/-(User(json))
          case Left(ParsingFailure(msg, error)) => -\/(UserServiceParsingError(msg, error))
        }
        case code => -\/(UserServiceFailure(code, body))
      }
    }
  }
}
