package com.cloudentity.edge.client

import com.cloudentity.edge.domain.flow.{ServiceClientName, SmartHttpClientConf}
import com.cloudentity.tools.vertx.conf.ConfService
import com.cloudentity.tools.vertx.scala.FutureConversions
import io.vertx.core.json.JsonObject
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.Try
import scalaz.{-\/, \/, \/-}

object SmartHttpConfsReader extends FutureConversions {
  val log = LoggerFactory.getLogger(this.getClass)

  def readAll(confService: ConfService, targetSmartClientsConfPath: String)(implicit ec: VertxExecutionContext): Future[Map[ServiceClientName, SmartHttpClientConf]] =
    confService.getConf(targetSmartClientsConfPath).toScala()
      .map(Option.apply)
      .map(readFromConfig(_, targetSmartClientsConfPath))
      .map(_.toEither.toTry)
      .flatMap(Future.fromTry)

  def readDefault(confService: ConfService, targetSmartClientsDefaultConfPath: String)(implicit ec: VertxExecutionContext): Future[Option[SmartHttpClientConf]] =
    confService.getConf(targetSmartClientsDefaultConfPath).toScala()
      .map(Option.apply)
      .map(_.map(SmartHttpClientConf.apply))

  def readFromConfig(confOpt: Option[JsonObject], targetSmartClientsConfPath: String): Throwable \/ Map[ServiceClientName, SmartHttpClientConf] =
    confOpt match {
      case Some(conf) =>
        log.info(s"Configuration of SmartHttpClients for target services: ${conf.toString()}")

        val smartConfigs: Iterable[(ServiceClientName, Option[SmartHttpClientConf])] =
          conf.fieldNames().asScala.map { serviceName =>
            ServiceClientName(serviceName) -> Try(conf.getJsonObject(serviceName)).toOption.map(SmartHttpClientConf.apply)
          }

        val wrongSmartConfigs = smartConfigs.filter(_._2.isEmpty)
        if (wrongSmartConfigs.isEmpty) {
          val result: Map[ServiceClientName, SmartHttpClientConf] =
            smartConfigs.flatMap { case (serviceName, confOpt) => confOpt.map(serviceName -> _) }.toMap

          \/-(result)
        } else {
          -\/(new Exception(s"Some configuration of SmartHttpClients are not JSON objects: ${wrongSmartConfigs.map(_._1.value).mkString(", ")}"))
        }
      case None =>
        log.info(s"No configuration of SmartHttpClients for target services at '$targetSmartClientsConfPath' config path. Using default configuration")
        \/-(Map())
    }
}