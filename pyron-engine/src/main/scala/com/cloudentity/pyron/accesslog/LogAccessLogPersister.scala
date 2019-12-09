package com.cloudentity.pyron.accesslog

import io.circe.syntax._
import io.circe.{Encoder, Json}
import io.circe.generic.semiauto._
import com.cloudentity.pyron.accesslog.AccessLogHandler.RequestLog
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.http.HttpVersion
import com.cloudentity.pyron.domain.Codecs._
import com.cloudentity.pyron.util.JsonUtil

class LogAccessLogPersister extends ScalaServiceVerticle with AccessLogPersister {

  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  override def persist(ctx: TracingContext, accessLog: AccessLog): Unit = {
    log.info(ctx, accessLog.asJson(Encoders.accessLogEncoder).noSpaces)
  }

}

object Encoders {
  implicit val noneAsDashOptionEncoder: Encoder[Option[String]] = Encoder.encodeString.contramap {
    case Some(s) => s
    case None => "-"
  }

  implicit val httpVersionEncoder: Encoder[HttpVersion] = Encoder.encodeString.contramap {
    case HttpVersion.HTTP_1_0 => "HTTP/1.0"
    case HttpVersion.HTTP_1_1 => "HTTP/1.1"
    case HttpVersion.HTTP_2 => "HTTP/2.0"
    case _ => "-"
  }

  implicit val requestLogEncoder: Encoder[RequestLog] = deriveEncoder[RequestLog]
  implicit val GatewayLogEncoder: Encoder[GatewayLog] = deriveEncoder

  val accessLogEncoder: Encoder[AccessLog] = (a: AccessLog) =>
    JsonUtil.deepMerge(
      Json.obj(
        ("timestamp", a.timestamp.toString.asJson),
        ("trueClientIp", a.trueClientIp.asJson),
        ("remoteClient", a.remoteClient.asJson),
        ("tracing", a.tracing.asJson),
        ("http", Json.obj(
          ("httpVersion", a.http.httpVersion.asJson),
          ("method", a.http.method.toString.asJson),
          ("uri", a.http.uri.asJson),
          ("status", a.http.status.getOrElse(0).toString.asJson)
        )),
        ("gateway", a.gateway.asJson),
        ("authnCtx", a.authnCtx.asJson),
        ("request", a.request.asJson),
        ("timeMs", a.timeMs.toString.asJson)
      ), Json.obj(a.extraItems.value.toSeq: _*)
    )
}


