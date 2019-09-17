package com.cloudentity.edge.client

import com.cloudentity.edge.config.Conf
import com.cloudentity.edge.domain._
import com.cloudentity.edge.domain.flow.{DiscoverableService, DiscoverableServiceRule, FixedHttpClientConf, ServiceClientName, SmartHttpClientConf, StaticService, TargetHost}
import com.cloudentity.edge.domain.http.{CallOpts, TargetRequest}
import com.cloudentity.edge.domain.rule.RuleConf
import com.cloudentity.tools.vertx.conf.ConfService
import com.cloudentity.tools.vertx.http.builder.SmartHttpClientBuilderImpl.CallOk
import com.cloudentity.tools.vertx.http.builder.{RequestCtxBuilder, SmartHttpResponse}
import com.cloudentity.tools.vertx.http.client.SmartHttpClientImpl
import com.cloudentity.tools.vertx.http.{SmartHttp, SmartHttpClient}
import com.cloudentity.tools.vertx.scala.FutureConversions
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext, TracingManager}
import io.vertx.core.buffer.Buffer
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import com.cloudentity.tools.vertx.sd.{Node => SdNode}
import com.cloudentity.tools.vertx.sd.circuit.NoopCB
import com.cloudentity.tools.vertx.sd.{Location, Sd, ServiceName}
import io.vertx.core
import io.vertx.core.Vertx
import io.vertx.core.http._
import org.slf4j.LoggerFactory
import scalaz._

import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

case class TargetResponse(http: HttpClientResponse, body: Buffer)

object TargetClient extends FutureConversions {
  val log = LoggerFactory.getLogger(this.getClass)

  def apply(vertx: Vertx, tracing: TracingManager, rules: List[RuleConf], smartHttpConfs: Map[ServiceClientName, SmartHttpClientConf], defaultSmartHttpConf: Option[SmartHttpClientConf], defaultFixedHttpConf: Option[FixedHttpClientConf])
           (implicit ec: VertxExecutionContext): Future[TargetClient] = {
    val serviceNames: Set[ServiceClientName] =
      rules.map(_.target).collect { case DiscoverableServiceRule(serviceClientName) => serviceClientName }.toSet
    val smartClients: Set[Future[(ServiceClientName, SmartHttpClient)]] =
      serviceNames.map { name =>
        smartHttpConfs.get(name).orElse(defaultSmartHttpConf) match {
          case Some(smartHttpConf) => buildSmartHttpClient(vertx, name, smartHttpConf).map(name -> _)
          case None                => buildSmartHttpClient(vertx, name).map(name -> _)
        }
      }

    val fixedClientOpts = defaultFixedHttpConf.map(_.value).map(new HttpClientOptions(_)).getOrElse(new HttpClientOptions())
    val fixedClient = vertx.createHttpClient(fixedClientOpts)
    Future.sequence(smartClients)
      .map(_.toMap)
      .map(clients => new TargetClient(tracing, fixedClient, clients))
  }

  def buildSmartHttpClient(vertx: Vertx, serviceName: ServiceClientName)
                          (implicit ec: VertxExecutionContext): Future[SmartHttpClient] =
    SmartHttp.clientBuilder(vertx, serviceName.value).build().toScala()

  def buildSmartHttpClient(vertx: Vertx, serviceName: ServiceClientName, smartHttpClientConf: SmartHttpClientConf)
                          (implicit ec: VertxExecutionContext): Future[SmartHttpClient] =
    SmartHttp.clientBuilder(vertx, serviceName.value, smartHttpClientConf.value).build().toScala()

  def resetTargetClient(vertx: Vertx, confService: ConfService, tracing: TracingManager, rules: List[RuleConf], oldTargetClientOpt: Option[TargetClient])
                       (implicit ec: VertxExecutionContext): Future[TargetClient] = {
    val resetFut =
      for {
        smartConfs      <- getSmartHttpConfs(confService)
        defaultSmartConf<- getDefaultSmartHttpConf(confService)
        defaultFixedConf<- getDefaultFixedHttpConf(confService)
        newTargetClient <- TargetClient(vertx, tracing, rules, smartConfs, defaultSmartConf, defaultFixedConf)
      } yield {
        oldTargetClientOpt.foreach { tc =>
          vertx.setTimer(60000, _ => tc.close()) // waiting 60s before closing old clients to let active connections finish
        }
        newTargetClient
      }
    resetFut.onComplete {
      case Success(_)  => log.debug("TargetClient reset successfully")
      case Failure(ex) => log.error("Could not reset TargetClient", ex)
    }
    resetFut
  }

  private def getDefaultSmartHttpConf(confService: ConfService)(implicit ec: VertxExecutionContext): Future[Option[SmartHttpClientConf]] =
    SmartHttpConfsReader.readDefault(confService, Conf.defaultSmartHttpClientKey)

  private def getDefaultFixedHttpConf(confService: ConfService)(implicit ec: VertxExecutionContext): Future[Option[FixedHttpClientConf]] =
    FixedHttpConfsReader.readDefault(confService, Conf.defaultFixedHttpClientKey)

  private def getSmartHttpConfs(confService: ConfService)(implicit ec: VertxExecutionContext): Future[Map[ServiceClientName, SmartHttpClientConf]] =
    SmartHttpConfsReader.readAll(confService, Conf.smartHttpClientsKey)
}

class TargetClient(tracing: TracingManager, fixedClient: HttpClient, clients: Map[ServiceClientName, SmartHttpClient])
                  (implicit ec: VertxExecutionContext) extends FutureConversions {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  def call(ctx: TracingContext, request: TargetRequest, callOpts: Option[CallOpts]): Future[Throwable \/ TargetResponse] = {
    log.debug(ctx, s"Forwarding request: $request")
    val childCtx = buildChildContext(ctx, request)
    val requestWithTracingHeaders = injectTracingHeaders(childCtx, request)

    val result = request.service match {
      case StaticService(host, port, ssl) =>
        callStaticService(ctx, Location(host.value, port, ssl, None), requestWithTracingHeaders, callOpts)
      case DiscoverableService(serviceName) =>
        callDiscoverableService(ctx, serviceName, requestWithTracingHeaders, callOpts)
    }

    result.onComplete {
      case scala.util.Success(\/-(response)) =>
        childCtx.setTag(io.opentracing.tag.Tags.HTTP_STATUS.getKey, response.http.statusCode().toString)
        childCtx.finish()
      case scala.util.Success(-\/(err)) =>
        childCtx.logError(err)
        childCtx.finish()
      case scala.util.Failure(ex) =>
        childCtx.logException(ex)
        childCtx.finish()
    }

    result
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
    case StaticService(host, port, ssl) => s"${host.value}:$port"
    case DiscoverableService(serviceName) => serviceName.value
  }

  private def callDiscoverableService(tracing: TracingContext, serviceName: ServiceClientName, request: TargetRequest, callOpts: Option[CallOpts]): Future[Throwable \/ TargetResponse] = {
    clients.get(serviceName) match {
      case Some(cli) => makeSmartCall(tracing, request, callOpts, None, cli)
      case None      => Future.failed(new Exception(s"Could not get SmartHttpClient for $serviceName"))
    }
  }

  private def makeSmartCall(tracing: TracingContext, request: TargetRequest, callOpts: Option[CallOpts], sd: Option[Sd], cli: SmartHttpClient) = {
    val reqBuilder =
      copyHeadersWithoutContentLength(request, cli.request(request.method, request.uri.value))

    val reqBuilderWithOpts = withCallOpts(reqBuilder, callOpts)

    val responseFut: Future[SmartHttpResponse] =
      request.bodyOpt match {
        case Some(body) =>
          reqBuilderWithOpts.endWithBody(tracing, body).toScala()
        case None =>
          reqBuilderWithOpts.endWithBody(tracing).toScala()
      }

    responseFut
      .map[Throwable \/ TargetResponse] { resp =>
        \/-(TargetResponse(resp.getHttp, resp.getBody))
      }.recover { case ex: Throwable => -\/(ex) }
  }

  private def withCallOpts(reqBuilder: RequestCtxBuilder, callOpts: Option[CallOpts]) =
    callOpts match {
      case Some(opts) =>
        val withResponseTimeout     = opts.responseTimeout.map(reqBuilder.responseTimeout).getOrElse(reqBuilder)
        val withRetries             = opts.retries.map(withResponseTimeout.retries).getOrElse(withResponseTimeout)
        val withFailureCodes        = opts.failureHttpCodes.map(codes => withRetries.responseFailure(resp => codes.contains(resp.getHttp().statusCode()))).getOrElse(withRetries)
        val withRetryFailedResponse = opts.retryFailedResponse.map(withFailureCodes.retryFailedResponse).getOrElse(withFailureCodes)
        val withRetryOnException    = opts.retryOnException.map(withRetryFailedResponse.retryOnException).getOrElse(withRetryFailedResponse)

        withRetryOnException
      case None =>
        reqBuilder
    }

  private def callStaticService(tracing: TracingContext, location: Location, request: TargetRequest, callOpts: Option[CallOpts]): Future[Throwable \/ TargetResponse] = {
    val sd = new Sd {
      val sn = ServiceName(s"http${if (location.ssl) "s" else ""}://${location.host}:${location.port}")
      override def discover(): Option[SdNode] = Some(SdNode(sn, new NoopCB(sn.value), location))
      override def serviceName(): ServiceName = sn
      override def close(): core.Future[Unit] = core.Future.succeededFuture(())
    }

    makeSmartCall(tracing, request, callOpts, Some(sd), new SmartHttpClientImpl(sd, fixedClient, 0, None, _ => CallOk, _ => false))
  }

  def copyHeadersWithoutContentLength(from: TargetRequest, to: HttpClientRequest): Unit = {
    for {
      (name, values) <- from.headers.toMap
      value          <- values
    } to.headers().add(name, value)

    dropContentLengthHeader(to)
  }

  /**
    * Plugins could have changed original body, without adjusting Content-Length.
    * We drop Content-Length and let Vertx http client set it.
    */
  private def dropContentLengthHeader(request: HttpClientRequest) =
    request.headers().remove("Content-Length")

  def copyHeadersWithoutContentLength(from: TargetRequest, to: RequestCtxBuilder): RequestCtxBuilder = {
    val headers: Iterable[(String, String)] =
      for {
        (name, values) <- from.headers.remove("Content-Length").toMap
        value          <- values.headOption
      } yield (name, value)

    headers.foldLeft(to) { case (builder, (name, value)) =>
      builder.putHeader(name, value)
    }
  }

  def close(): Future[Unit] = {
    fixedClient.close()
    Future.sequence(
      clients.values.map(_.close().toScala())
    ).map(_ => ())
  }
}
