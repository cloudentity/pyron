package com.cloudentity.pyron.client

import com.cloudentity.pyron.config.Conf
import com.cloudentity.pyron.domain.flow._
import com.cloudentity.pyron.domain.http.{CallOpts, Headers, TargetRequest}
import com.cloudentity.pyron.domain.rule.RuleConf
import com.cloudentity.tools.vertx.conf.ConfService
import com.cloudentity.tools.vertx.http.SmartHttp.clientBuilder
import com.cloudentity.tools.vertx.http.builder.RequestCtxBuilder
import com.cloudentity.tools.vertx.http.builder.SmartHttpClientBuilderImpl.CallOk
import com.cloudentity.tools.vertx.http.circuit.NoopCB
import com.cloudentity.tools.vertx.http.client.SmartHttpClientImpl
import com.cloudentity.tools.vertx.http.{Sd, SmartHttpClient}
import com.cloudentity.tools.vertx.scala.{FutureConversions, VertxExecutionContext}
import com.cloudentity.tools.vertx.sd.{Location, ServiceName, Node => SdNode}
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext, TracingManager}
import io.opentracing.tag.Tags.HTTP_STATUS
import io.vertx.core
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http._
import io.vertx.core.streams.ReadStream
import org.slf4j.{Logger, LoggerFactory}
import scalaz.{-\/, \/, \/-}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success}

case class TargetResponse(http: HttpClientResponse, body: Buffer)

object TargetClient extends FutureConversions {

  val log: Logger = LoggerFactory.getLogger(this.getClass)

  def apply(vertx: Vertx,
            tracing: TracingManager,
            rules: List[RuleConf],
            smartHttpConfs: Map[ServiceClientName, SmartHttpClientConf],
            defaultSmartHttpConf: Option[SmartHttpClientConf],
            defaultFixedHttpConf: Option[FixedHttpClientConf]
           )(implicit ec: VertxExecutionContext): Future[TargetClient] = {

    val serviceNames: Set[ServiceClientName] = rules.map(_.target).collect {
      case DiscoverableServiceRule(serviceClientName) => serviceClientName
    }.toSet

    Future.sequence {
      buildSmartHttpClients(vertx, serviceNames, smartHttpConfs, defaultSmartHttpConf)
    } map { clients =>
      val fixedClient = vertx.createHttpClient(defaultFixedHttpConf.map(_.value)
        .fold(new HttpClientOptions())(new HttpClientOptions(_)))
      new TargetClient(tracing, fixedClient, clients.toMap)
    }
  }

  def buildSmartHttpClients(vertx: Vertx,
                            serviceNames: Set[ServiceClientName],
                            smartHttpConfs: Map[ServiceClientName, SmartHttpClientConf],
                            defaultSmartHttpConf: Option[SmartHttpClientConf]
                           )(implicit ec: VertxExecutionContext): Set[Future[(ServiceClientName, SmartHttpClient)]] =
    serviceNames.map { serviceName =>
      smartHttpConfs.get(serviceName).orElse(defaultSmartHttpConf).fold {
        clientBuilder(vertx, serviceName.value)
      } { smartHttpClientConf =>
        clientBuilder(vertx, serviceName.value, smartHttpClientConf.value)
      }.build().toScala().map(serviceName -> _)
    }

  def resetTargetClient(vertx: Vertx,
                        confService: ConfService,
                        tracing: TracingManager,
                        rules: List[RuleConf],
                        oldTargetClientOpt: Option[TargetClient]
                       )(implicit ec: VertxExecutionContext): Future[TargetClient] = {

    val resetFuture = for {
      smartConfs <- SmartHttpConfsReader.readAll(confService, Conf.smartHttpClientsKey)
      defaultSmartConf <- SmartHttpConfsReader.readDefault(confService, Conf.defaultSmartHttpClientKey)
      defaultFixedConf <- FixedHttpConfsReader.readDefault(confService, Conf.defaultFixedHttpClientKey)
      newTargetClient <- TargetClient(vertx, tracing, rules, smartConfs, defaultSmartConf, defaultFixedConf)
    } yield {
      // wait 60s before closing old clients to let active connections finish
      oldTargetClientOpt.foreach(tc => vertx.setTimer(60000, _ => tc.close()))
      newTargetClient
    }

    resetFuture.onComplete {
      case Success(_) => log.debug("TargetClient reset successfully")
      case Failure(ex) => log.error("Could not reset TargetClient", ex)
    }
    resetFuture
  }

}

class TargetClient(tracing: TracingManager,
                   fixedClient: HttpClient,
                   clients: Map[ServiceClientName, SmartHttpClient]
                  )(implicit ec: VertxExecutionContext) extends FutureConversions {

  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  def call(ctx: TracingContext,
           request: TargetRequest,
           bodyStreamOpt: Option[ReadStream[Buffer]],
           callOpts: Option[CallOpts]
          ): Future[Throwable \/ TargetResponse] = {

    log.debug(ctx, s"Forwarding request: $request")
    val childCtx = buildChildContext(ctx, request)
    val requestWithTracingHeaders = injectTracingHeaders(childCtx, request)

    val responseFuture = request.service match {
      case StaticService(host, port, ssl) =>
        val location = Location(host.value, port, ssl, root = None)
        callStaticService(ctx, location, requestWithTracingHeaders, bodyStreamOpt, callOpts)
      case DiscoverableService(serviceName) =>
        callDiscoverableService(ctx, serviceName, requestWithTracingHeaders, bodyStreamOpt, callOpts)
      case RerouteService(rewritePath) =>
        Future.failed(new Exception(s"RerouteService cannot be called directly: $rewritePath"))
    }

    onCallComplete(childCtx, responseFuture)

    responseFuture
  }

  private def injectTracingHeaders(ctx: TracingContext, request: TargetRequest): TargetRequest = {
    val headers = ctx.getSpanContextMap.iterator().asScala.map(e => e.getKey -> e.getValue).toMap
    request.modifyHeaders(_.setHeaders(headers))
  }

  private def buildChildContext(parentCtx: TracingContext, request: TargetRequest): TracingContext = {
    val childCtx = parentCtx.newChild(tracing, getOperationName(request))
    childCtx.setTag(io.opentracing.tag.Tags.HTTP_METHOD.getKey, request.method.toString)
    childCtx.setTag(io.opentracing.tag.Tags.HTTP_URL.getKey, request.uri.value)
    childCtx
  }

  private def getOperationName(request: TargetRequest): String = request.service match {
    case StaticService(host, port, _) => s"${host.value}:$port"
    case DiscoverableService(serviceName) => serviceName.value
    case RerouteService(rewritePath) => s"reroute:${rewritePath.value}"
  }

  private def onCallComplete(ctx: TracingContext, responseFuture: Future[Throwable \/ TargetResponse]): Unit = {
    responseFuture.andThen {
      case Success(\/-(response)) => ctx.setTag(HTTP_STATUS.getKey, response.http.statusCode().toString)
      case Success(-\/(err)) => ctx.logError(err)
      case Failure(ex) => ctx.logException(ex)
    }.onComplete(_ => ctx.finish())
  }

  private def callDiscoverableService(tracing: TracingContext,
                                      serviceName: ServiceClientName,
                                      request: TargetRequest,
                                      bodyStreamOpt: Option[ReadStream[Buffer]],
                                      callOpts: Option[CallOpts]): Future[Throwable \/ TargetResponse] = {
    clients.get(serviceName) match {
      case Some(cli) => makeSmartCall(tracing, request, bodyStreamOpt, callOpts, cli)
      case None      => Future.failed(new Exception(s"Could not get SmartHttpClient for $serviceName"))
    }
  }

  private def makeSmartCall(tracing: TracingContext,
                            request: TargetRequest,
                            bodyStreamOpt: Option[ReadStream[Buffer]],
                            callOpts: Option[CallOpts],
                            cli: SmartHttpClient
                           ): Future[Throwable \/ TargetResponse] = {

    val reqBuilder = withCallOpts(cli.request(request.method, request.uri.value), callOpts)

    val responseFuture = request.bodyOpt match {
      case Some(body) => copyHeadersDropContentLength(request.headers, reqBuilder).endWithBody(tracing, body)
      case None => bodyStreamOpt match {
        case Some(stream) => copyHeaders(request.headers, reqBuilder).endWithBody(tracing, stream)
        case None => copyHeaders(request.headers, reqBuilder).endWithBody(tracing)
      }
    }

    responseFuture.toScala().map[Throwable \/ TargetResponse] {
      resp => \/-(TargetResponse(resp.getHttp, resp.getBody))
    } recover { case ex: Throwable => -\/(ex) }
  }

  private def withCallOpts(reqBuilder: RequestCtxBuilder, callOpts: Option[CallOpts]): RequestCtxBuilder =
    callOpts match {
      case Some(opts) =>
        val withResponseTimeout     = opts.responseTimeout.map(reqBuilder.responseTimeout).getOrElse(reqBuilder)
        val withRetries             = opts.retries.map(withResponseTimeout.retries).getOrElse(withResponseTimeout)
        val withFailureCodes        = opts.failureHttpCodes.map(codes => withRetries.responseFailure(resp => codes.contains(resp.getHttp.statusCode()))).getOrElse(withRetries)
        val withRetryFailedResponse = opts.retryFailedResponse.map(withFailureCodes.retryFailedResponse).getOrElse(withFailureCodes)
        val withRetryOnException    = opts.retryOnException.map(withRetryFailedResponse.retryOnException).getOrElse(withRetryFailedResponse)

        withRetryOnException
      case None =>
        reqBuilder
    }

  private def callStaticService(tracing: TracingContext,
                                location: Location,
                                request: TargetRequest,
                                bodyStreamOpt: Option[ReadStream[Buffer]],
                                callOpts: Option[CallOpts]
                               ): Future[Throwable \/ TargetResponse] = {
    val sd: Sd = new Sd {
      val sn: ServiceName = ServiceName(s"http${if (location.ssl) "s" else ""}://${location.host}:${location.port}")
      override def discover(): Option[SdNode] = Option(SdNode(sn, new NoopCB(sn.value), location))
      override def serviceName(): ServiceName = sn
      override def close(): core.Future[Unit] = core.Future.succeededFuture(())
    }

    val smartClient = new SmartHttpClientImpl(sd, fixedClient, 0, None, _ => CallOk, _ => true)
    makeSmartCall(tracing, request, bodyStreamOpt, callOpts, smartClient)
  }

  /**
    * Plugins could have changed original body, without adjusting Content-Length.
    * We drop Content-Length and let Vertx http client set it.
    */
  def copyHeadersDropContentLength(from: Headers, to: RequestCtxBuilder): RequestCtxBuilder =
    copyHeaders(from.remove("Content-Length"), to)

  def copyHeaders(from: Headers, to: RequestCtxBuilder): RequestCtxBuilder =
    from.toMap.foldLeft(to) { case (builder, (name, values)) =>
      values.foldLeft(builder) { case (b, value) => b.addHeader(name, value) }
    }

  def close(): Future[Unit] = {
    fixedClient.close()
    Future.sequence(clients.values.map(_.close().toScala())).map(_ => ())
  }

}
