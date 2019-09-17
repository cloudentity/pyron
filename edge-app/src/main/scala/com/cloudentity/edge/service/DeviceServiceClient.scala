package com.cloudentity.edge.service

import io.circe.parser.{decode, parse}
import io.circe.{Json, ParsingFailure}
import com.cloudentity.edge.commons.ClientWithTracing
import com.cloudentity.edge.domain.Codecs._
import com.cloudentity.edge.jwt.{JwtService, JwtServiceFactory, JwtToken}
import com.cloudentity.edge.service.DeviceServiceClient._
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

object DeviceServiceClient {
  case class DeviceServiceConf(deviceService: JsonObject, jwtServiceAddress: String)

  def apply(vertx: Vertx, rawConfig: JsonObject)(implicit ex: VertxExecutionContext): Future[\/[Throwable, DeviceServiceClient]] = {
    import io.circe.generic.auto._

    decode[DeviceServiceConf](rawConfig.toString) match {
      case Right(conf) =>
        val jwtService = JwtServiceFactory.createClient(vertx, conf.jwtServiceAddress)
        Futures.toScala(SmartHttp.clientBuilder(vertx, conf.deviceService).build()).map { smartClient =>
          \/-(new DeviceServiceClient(smartClient, jwtService))
        }
      case Left(error) =>
        Future.failed(new Exception("Failed to decode DeviceServiceConf: " + error.getMessage))
    }
  }

  case class Device(json: Json)
  sealed trait DeviceServiceError
  case class DeviceServiceFailure(code: Int, body: Buffer) extends DeviceServiceError
  case class DeviceServiceParsingError(msg: String, err: Throwable) extends DeviceServiceError
  case class DeviceServiceHttpError(err: Throwable) extends DeviceServiceError
}

class DeviceServiceClient(client: SmartHttpClient, jwtService: JwtService)
                         (implicit ec: VertxExecutionContext) extends FutureConversions with ClientWithTracing {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  def getDevice(ctx: TracingContext, uuid: String): Future[DeviceServiceError \/ Device] =
    jwtService.emptySigned.toScala().flatMap { case (jwt, auth) =>
      log.debug(ctx, s"Calling DeviceService to get device [uuid=$uuid, jwt=$jwt]")

      client.get(s"/devices/$uuid")
        .putHeader("Authorization", s"Bearer $auth")
        .withTracing(ctx)
        .endWithBody(ctx).toScala()
        .map(successHandler(ctx, jwt))
        .recover { case ex: Throwable => -\/(DeviceServiceHttpError(ex)) }
    }

  def successHandler(ctx: TracingContext, jwt: JwtToken): SmartHttpResponse => DeviceServiceError \/ Device =
    response => {
      log.debug(ctx, s"Received ${response.getHttp.statusCode()} status code from DeviceService, jwt=$jwt")
      val body = response.getBody
      response.getHttp.statusCode() match {
        case 200  => parse(body.toString) match {
          case Right(json)                      => \/-(Device(json))
          case Left(ParsingFailure(msg, error)) => -\/(DeviceServiceParsingError(msg, error))
        }
        case code => -\/(DeviceServiceFailure(code, body))
      }
    }
}
