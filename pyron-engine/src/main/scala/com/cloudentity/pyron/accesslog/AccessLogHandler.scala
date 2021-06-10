package com.cloudentity.pyron.accesslog

import java.time.{Instant, ZoneId}
import io.circe.{Json, JsonObject}
import com.cloudentity.pyron.accesslog.AccessLogHandler.RequestLog
import com.cloudentity.pyron.accesslog.AccessLogHelper.{AccessLogConf, LogAllFields, LogWhitelistedFields, MaskFieldsConf}
import com.cloudentity.pyron.api._
import com.cloudentity.pyron.commons.JsonUtils
import com.cloudentity.pyron.domain.http.Headers
import com.cloudentity.tools.vertx.bus.VertxEndpointClient
import com.cloudentity.tools.vertx.server.api.RouteHandler
import com.cloudentity.tools.vertx.server.api.tracing.RoutingWithTracingS
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingManager}
import io.vertx.core.http.{HttpMethod, HttpServerRequest, HttpVersion}
import io.vertx.core.{Handler, Vertx}
import com.cloudentity.pyron.api.FlowState
import com.cloudentity.pyron.domain.flow._
import io.vertx.core.net.SocketAddress
import io.vertx.ext.web.RoutingContext

import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.matching.Regex

object AccessLogHandler extends AccessLogHelper {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  val targetHostNamespaceRegex: Regex = """[^.]*\.([^.]*)""".r
  val targetServiceNamespaceRegex: Regex = """[^.]*\.([^.]*)""".r

  val wasLoggedOnBodyEndCtxKey = "access-log.logged-on-body-end"

  case class AccessLogGlobalConf(authnCtx: Option[Map[String, String]], request: Option[RequestLogConf], targetHostNamespace: Option[Boolean])
  case class RequestLogConf(headers: Option[AccessLogConf])

  case class RequestLog(headers: Option[Headers])

  def createHandler(vertx: Vertx, tracing: TracingManager, conf: Option[AccessLogGlobalConf]): Handler[RoutingContext] = {
    val accessLogPersister = VertxEndpointClient.make(vertx, classOf[AccessLogPersister])
    handle(tracing)(conf, accessLogPersister)
  }

  def handle(tracing: TracingManager)(conf: Option[AccessLogGlobalConf], accessLogPersister: AccessLogPersister)(ctx: RoutingContext): Unit = {
    val tracingCtx = RoutingWithTracingS.getOrCreate(ctx, tracing)
    ctx.put(RouteHandler.urlPathKey, Option(ctx.request().path()).getOrElse(""))
    tracingCtx.setOperationName(Option(ctx.request().path()).getOrElse(""))
    val timestamp = System.currentTimeMillis()

    val isoDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toInstant

    /*
      First, try to log response in body-end-handler. If the connection was interrupted (e.g. reset by the client), then
      the handler is not invoked. In that case the log is created in the end-handler.
     */
    ctx.addBodyEndHandler { _ =>
      responseHandler(tracing, conf, ctx, isoDate, timestamp, accessLogPersister, interrupted = false).handle(())
      ctx.put(wasLoggedOnBodyEndCtxKey, "logged")
    }

    ctx.response().endHandler { _ =>
      val wasLoggedOnBodyEnd = Option(ctx.get(wasLoggedOnBodyEndCtxKey)).isDefined
      if (!wasLoggedOnBodyEnd) {
        responseHandler(tracing, conf, ctx, isoDate, timestamp, accessLogPersister, interrupted = true).handle(())
      }
    }
    ctx.next()
  }

  def responseHandler(tracing: TracingManager, conf: Option[AccessLogGlobalConf], ctx: RoutingContext, isoDate: Instant, timestamp: Long, accessLogPersister: AccessLogPersister, interrupted: Boolean): Handler[Unit] =
    (_: Unit) => {
      val req = ctx.request()
      val resp = ctx.response()

      val tracingContext = RoutingWithTracingS.getOrCreate(ctx, tracing)
      val tracingContextMap: Map[String, String] = tracingContext
        .getSpanContextMap.iterator().asScala
        .map(e => e.getKey -> e.getValue).toMap
      tracingContext.setTag(io.opentracing.tag.Tags.HTTP_STATUS.getKey, ctx.response().getStatusCode.toString)

      val flowState = RoutingCtxData.getFlowState(ctx)
      val gatewayLog = buildGatewayLog(flowState, interrupted)
      val extraAccessLog = extraAccessLogWithTargetHostNamespace(conf, flowState, gatewayLog)

      val log = AccessLog(
        isoDate,
        getTrueClientIp(ctx),
        getClientAddress(req.remoteAddress()),
        tracingContextMap,

        HttpParams(
          httpVersion = req.version(),
          method = req.method,
          uri = req.uri(),
          status = if (interrupted) None else Some(resp.getStatusCode)
        ),
        getAuthnCtxSignature(ctx, conf),
        getRequestLog(req, conf),
        System.currentTimeMillis() - timestamp,
        extraAccessLog,
        gatewayLog,
        flowState.properties
      )

      accessLogPersister.persist(tracingContext, log)

    }

  private def extraAccessLogWithTargetHostNamespace(conf: Option[AccessLogGlobalConf], flowState: FlowState, gatewayLog: GatewayLog): AccessLogItems = {
    def readTargetHostNamespace(targetHostNameOpt: Option[TargetHost]): Option[String] = {
      for {
        m         <- targetHostNameOpt.flatMap(name => targetHostNamespaceRegex.findFirstMatchIn(name.value))
        namespace <- Try(m.group(1)).toOption
      } yield namespace
    }

    def readTargetServiceNamespace(targetServiceNameOpt: Option[ServiceClientName]): Option[String] = {
      for {
        m         <- targetServiceNameOpt.flatMap(name => targetServiceNamespaceRegex.findFirstMatchIn(name.value))
        namespace <- Try(m.group(1)).toOption
      } yield namespace
    }

    if (conf.flatMap(_.targetHostNamespace).getOrElse(false)) {
      readTargetHostNamespace(gatewayLog.targetHost) match {
        case Some(namespace) => flowState.extraAccessLogs.updated("namespace", Json.fromString(namespace))
        case None            =>
          readTargetServiceNamespace(gatewayLog.targetService) match {
            case Some(namespace) => flowState.extraAccessLogs.updated("namespace", Json.fromString(namespace))
            case None            => flowState.extraAccessLogs
          }
      }
    } else flowState.extraAccessLogs
  }

  private def buildGatewayLog(flowState: FlowState, interrupted: Boolean) = {
    val (targetServiceNameOpt, targetHostOpt, targetPortOpt): (Option[ServiceClientName], Option[TargetHost], Option[Int]) =
      flowState.rules.headOption.map(_.conf.target) match {
        case Some(DiscoverableServiceRule(name))    => (Some(name), None, None)
        case Some(StaticServiceRule(host, port, _)) => (None, Some(host), Some(port))
        case Some(ProxyServiceRule)                 => (None, None, None)
        case Some(RerouteServiceRule(_))            => (None, None, None)
        case None                                   => (None, None, None)
      }

    GatewayLog(
      method        = flowState.rules.headOption.map(_.conf.criteria.method),
      path          = flowState.rules.headOption.map(_.conf.criteria.rewrite.matchPattern),
      pathPrefix    = flowState.rules.headOption.map(_.conf.criteria.rewrite.pathPrefix),
      aborted       = flowState.aborted.getOrElse(true),
      interrupted   = interrupted,
      failed        = flowState.failure.map(_ => true),
      targetService = targetServiceNameOpt,
      targetHost    = targetHostOpt,
      targetPort    = targetPortOpt
    )
  }

  def getClientAddress(inetSocketAddress: SocketAddress): Option[String] =
    Option(inetSocketAddress.host())

  def getTrueClientIp(ctx: RoutingContext): Option[String] =
    ProxyHeadersHandler.getProxyHeaders(ctx).map(_.trueClientIp)

  def getAuthnCtxSignature(ctx: RoutingContext, confOpt: Option[AccessLogGlobalConf]): JsonObject = {
    val signatureOpt =
      selectFromAuthnCtx(RoutingCtxData.getFlowState(ctx).authnCtx, confOpt)

    signatureOpt.getOrElse(JsonObject.empty)
  }

  def selectFromAuthnCtx(authnCtxOpt: Option[AuthnCtx], confOpt: Option[AccessLogGlobalConf]): Option[JsonObject] =
    for {
      conf        <- confOpt
      authnCtxConf <- conf.authnCtx
      authnCtx     <- authnCtxOpt
    } yield {
      val obj = Json.obj(authnCtx.value.toList:_*)
      authnCtxConf.foldLeft(JsonObject.empty) { case (out, (key, path)) =>
        JsonUtils.find(obj, path).map(out.add(key, _)).getOrElse(out)
      }
    }

  def getRequestLog(req: HttpServerRequest, confOpt: Option[AccessLogGlobalConf]): Option[RequestLog] =
    confOpt.flatMap(_.request).flatMap(_.headers).map { conf =>
      val maskFields = conf.maskFields.getOrElse(MaskFieldsConf(None, None))

      def maskHeader(header: String, values: Iterable[String]): Iterable[String] = {
        if (maskFields.whole.getOrElse(List()).contains(header)) values.map(_ => "******")
        else if (maskFields.partial.getOrElse(List()).contains(header)) values.map(maskPartially)
        else values
      }

      def getHeaders(names: Iterable[String]): Headers = {
        names.foldLeft(Headers()) { (acc, header) =>
          val headerValues = maskHeader(header, req.headers.getAll(header).asScala)
          acc.addValues(header, headerValues.toList)
        }
      }

      conf.typeConf match {
        case LogAllFields(true)            => RequestLog(Some(getHeaders(req.headers().names().asScala)))
        case LogWhitelistedFields(headers) => RequestLog(Some(getHeaders(headers)))
        case _                              => RequestLog(None)
      }
    }

  def formatHttpVersion(hv: HttpVersion): String =
    hv match {
      case HttpVersion.HTTP_1_0 => "HTTP/1.0"
      case HttpVersion.HTTP_1_1 => "HTTP/1.1"
      case HttpVersion.HTTP_2   => "HTTP/2.0"
      case _                    => "-"
    }
}

case class GatewayLog(
  method: Option[HttpMethod],
  path: Option[String],
  pathPrefix: Option[String],
  aborted: Boolean,
  interrupted: Boolean,
  failed: Option[Boolean],
  targetService: Option[ServiceClientName],
  targetHost: Option[TargetHost],
  targetPort: Option[Int]
)

case class AccessLog(
  timestamp: Instant,
  trueClientIp: Option[String],
  remoteClient: Option[String],
  tracing: Map[String, String],
  http: HttpParams,
  authnCtx: JsonObject,
  request: Option[RequestLog],
  timeMs: Long,
  extraItems: AccessLogItems,
  gateway: GatewayLog,
  properties: Properties
)

case class HttpParams(
  httpVersion: HttpVersion,
  method: HttpMethod,
  uri: String,
  status: Option[Int]
)
