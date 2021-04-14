package com.cloudentity.pyron

import com.cloudentity.tools.vertx.conf.ConfService
import com.cloudentity.tools.vertx.scala.{FutureConversions, VertxExecutionContext}
import io.vertx.core.json.JsonObject

import scala.concurrent.Future

package object client extends FutureConversions {

  private[client] def getConf(confService: ConfService, path: String)
             (implicit ec: VertxExecutionContext): Future[Option[JsonObject]] =
    confService.getConf(path).toScala().map(Option(_))

}
