package com.cloudentity.pyron.api

import com.cloudentity.pyron.config.Conf.AppConf
import com.cloudentity.tools.vertx.http.HttpService
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.server.api.{ApiServer => VxApiServer}
import io.vertx.core.http.{HttpServer, HttpServerOptions}
import io.vertx.core.json.JsonObject
import io.vertx.core.{Future => VxFuture}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Future

class ApiServer(conf: AppConf) extends ScalaServiceVerticle with HttpService {
  val log: Logger = LoggerFactory.getLogger(this.getClass)

  var actualPort: Int = _

  override def configPath(): String = "app.server"

  override def vertxServiceAddressPrefixS: Option[String] = Some(VxApiServer.defaultVerticleId)

  override def initServiceAsyncS(): Future[Unit] = {
    val router = ApiRouter.createRouter(vertx, conf, getTracing)
    val opts = new HttpServerOptions(getHttpConfig)
    asFuture[HttpServer](vertx.createHttpServer(opts).requestHandler(router).listen(_)).toScala()
      .map { server =>
        actualPort = server.actualPort()
        log.info(s"Started HTTP server on port $actualPort")
        ()
      }
  }

  private def getHttpConfig = {
    val httpConfig = Option(getConfig).getOrElse(new JsonObject()).getJsonObject("http", new JsonObject())
    conf.port.foreach(httpConfig.put("port", _)) // we use app.port conf value for backward-compatibility
    httpConfig
  }

  override def getActualPort(): VxFuture[Int] =
    VxFuture.succeededFuture(actualPort)
}
