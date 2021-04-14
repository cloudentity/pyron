package com.cloudentity.pyron.client

import com.cloudentity.pyron.domain.flow.FixedHttpClientConf
import com.cloudentity.tools.vertx.conf.ConfService
import com.cloudentity.tools.vertx.scala.VertxExecutionContext

import scala.concurrent.Future

object FixedHttpConfsReader {

  def readDefault(confService: ConfService, targetFixedClientDefaultConfPath: String)
                 (implicit ec: VertxExecutionContext): Future[Option[FixedHttpClientConf]] =
    getConf(confService, targetFixedClientDefaultConfPath)
      .map(_.map(FixedHttpClientConf))

}
