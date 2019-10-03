package com.cloudentity.pyron.api

import com.cloudentity.pyron.config.Conf.AppConf
import com.cloudentity.tools.vertx.scala.bus.ScalaComponentVerticle
import io.vertx.core.json.JsonObject
import io.vertx.core.http.{HttpServer, HttpServerOptions}

import scala.concurrent.Future

class ApiServer(conf: AppConf) extends ScalaComponentVerticle {
  override def configPath(): String = "app.server"

  override def initComponentAsyncS(): Future[Unit] = {
    val router = ApiRouter.createRouter(vertx, conf, getTracing)
    val opts = new HttpServerOptions(getHttpConfig)
    asFuture[HttpServer](vertx.createHttpServer(opts).requestHandler(router).listen(_)).map(()).toScala()
  }

  private def getHttpConfig = {
    val httpConfig = Option(getConfig).getOrElse(new JsonObject()).getJsonObject("http", new JsonObject())
    conf.port.foreach(httpConfig.put("port", _)) // we use app.port conf value for backward-compatibility
    httpConfig
  }
}
