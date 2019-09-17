package com.cloudentity.edge.accesslog

import java.util
import java.util.UUID.randomUUID

import com.cloudentity.libs.channels.api.java.{Builders, Channels, MessageContext}
import com.cloudentity.libs.channels.kafka.{KafkaConfig, KafkaOutputChannel}
import com.cloudentity.libs.events.access.{AccessLog => KafkaAccessLog}
import com.google.protobuf.{ByteString, Timestamp}
import io.circe.Printer
import io.circe.syntax._
import com.cloudentity.edge.accesslog.KafkaAccessLogPersister._
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.http.HttpVersion
import io.vertx.core.json.{JsonObject => VertxJsonObject}

import scala.collection.JavaConverters._

object KafkaAccessLogPersister {
  case class KafkaAccessLogPersisterConfig(
    kafkaConfig: KafkaConfig,
    topic: String
  )

  object KafkaAccessLogPersisterConfig {
    def apply(json: VertxJsonObject): KafkaAccessLogPersisterConfig = {
      KafkaAccessLogPersisterConfig(
        kafkaConfig =  KafkaConfig.fromJsonWithDefaults(json.getJsonObject("kafkaConfig")),
        topic = json.getString("topic")
      )
    }
  }
}

class KafkaAccessLogPersister extends ScalaServiceVerticle with AccessLogPersister {

  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)


  var producer: Channels.Producer[KafkaAccessLog] = _
  var accessLogEncoder: Channels.Encoder[KafkaAccessLog] = _

  val bytePrinter = Printer(
    preserveOrder = true,
    dropNullValues = false,
    indent = ""
  )

  override def initService(): Unit = {
    val conf = KafkaAccessLogPersisterConfig(getConfig)
    initProducer(conf)
  }

  def initProducer(config: KafkaAccessLogPersisterConfig): Unit = {
    accessLogEncoder = Builders.createEncoder(config.topic, _ => randomUUID().toString)
    producer = KafkaOutputChannel.startProducer(vertx, config.kafkaConfig, accessLogEncoder)
  }

  override def persist(ctx: TracingContext, accessLog: AccessLog): Unit = {
    val ctxMap: java.util.Map[String, String] = new util.HashMap[String, String]()
    ctx.consumeContext((t: String, u: String) => ctxMap.put(t, u))
    producer.send(convertToKafka(accessLog), MessageContext.of(ctxMap))
  }

  def convertToKafka(log: AccessLog): KafkaAccessLog = {
    val builder = KafkaAccessLog.newBuilder()

    builder.setTimestamp(
      Timestamp.newBuilder()
        .setSeconds(log.timestamp.getEpochSecond)
        .setNanos(log.timestamp.getNano)
        .build()
    )

    log.trueClientIp
      .map(builder.setTrueClientIp)

    log.remoteClient
      .map(builder.setRemoteClient)

    log.correlationIds
      .map(cids => {
        builder.setCorrelationIds(
          KafkaAccessLog.CorrelationIds.newBuilder()
          .setLocal(cids.local)
          .addAllExternal(cids.external.asJava)
          .build()
        )
      })

    builder.setHttp(
      KafkaAccessLog.Http.newBuilder()
        .setHttpVersion(log.http.httpVersion match {
          case HttpVersion.HTTP_1_0 => "HTTP/1.0"
          case HttpVersion.HTTP_1_1 => "HTTP/1.1"
          case HttpVersion.HTTP_2 => "HTTP/2.0"
          case _ => "-"
        })
        .setMethod(log.http.method.toString)
        .setUri(log.http.uri)
        .setStatus(log.http.status.getOrElse(0))
        .build()
    )

    builder.setAuthnCtx(
      ByteString.copyFrom(bytePrinter.prettyByteBuffer(log.authnCtx.asJson))
    )

    builder.setTimeMs(log.timeMs)

    builder.build()
  }

}

