package com.cloudentity.edge.client

import com.cloudentity.edge.domain.flow.FixedHttpClientConf
import com.cloudentity.tools.vertx.conf.ConfService
import com.cloudentity.tools.vertx.scala.FutureConversions
import com.cloudentity.tools.vertx.scala.VertxExecutionContext

import scala.concurrent.Future

object FixedHttpConfsReader extends FutureConversions {

  def readDefault(confService: ConfService, targetFixedClientDefaultConfPath: String)(implicit ec: VertxExecutionContext): Future[Option[FixedHttpClientConf]] =
    confService.getConf(targetFixedClientDefaultConfPath).toScala()
      .map(Option.apply)
      .map(_.map(FixedHttpClientConf.apply))

}
