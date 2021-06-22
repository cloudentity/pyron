package com.cloudentity.pyron.client

import com.cloudentity.pyron.domain.flow.{ServiceClientName, SmartHttpClientConf}
import com.cloudentity.tools.vertx.conf.ConfService
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import io.vertx.core.json.JsonObject
import org.slf4j.{Logger, LoggerFactory}
import scalaz.{-\/, \/, \/-}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.Try

object SmartHttpConfsReader {
  val log: Logger = LoggerFactory.getLogger(this.getClass)

  def readAll(confService: ConfService, targetSmartClientsConfPath: String)
             (implicit ec: VertxExecutionContext): Future[Map[ServiceClientName, SmartHttpClientConf]] =
    getConf(confService, targetSmartClientsConfPath).flatMap(confOpt =>
      Future.fromTry(readFromConfig(confOpt, targetSmartClientsConfPath).toEither.toTry))

  def readDefault(confService: ConfService, targetSmartClientsDefaultConfPath: String)
                 (implicit ec: VertxExecutionContext): Future[Option[SmartHttpClientConf]] =
    getConf(confService, targetSmartClientsDefaultConfPath)
      .map(_.map(SmartHttpClientConf))

  def readFromConfig(confOpt: Option[JsonObject],
                     targetSmartClientsConfPath: String
                    ): Throwable \/ Map[ServiceClientName, SmartHttpClientConf] = confOpt match {
    case None =>
      log.debug(s"No configuration of SmartHttpClients for target services at '$targetSmartClientsConfPath' config path. Using default configuration")
      \/-(Map())
    case Some(conf) =>
      log.debug(s"Configuration of SmartHttpClients for target services: ${conf.toString}")

      val smartConfigs = conf.fieldNames().asScala.map(serviceName =>
        ServiceClientName(serviceName) -> Try(conf.getJsonObject(serviceName)).toOption.map(SmartHttpClientConf))

      val wrongSmartConfigs = smartConfigs.filter { case (_, confOpt) => confOpt.isEmpty }

      if (wrongSmartConfigs.isEmpty) {
        \/-(smartConfigs.collect { case (serviceName, Some(conf)) => serviceName -> conf }.toMap)
      } else {
        val namesOfWrongConfigs = wrongSmartConfigs.map { case (name, _) => name.value }.mkString(", ")
        -\/(new Exception(s"Some configuration of SmartHttpClients are not JSON objects: $namesOfWrongConfigs"))
      }
  }
}