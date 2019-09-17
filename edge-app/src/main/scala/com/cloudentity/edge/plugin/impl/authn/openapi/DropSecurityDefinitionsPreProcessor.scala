package com.cloudentity.edge.plugin.impl.authn.openapi

import java.util

import io.circe.generic.auto._
import com.cloudentity.edge.openapi.OpenApiPreProcessor
import com.cloudentity.edge.util.ConfigDecoder
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import io.swagger.models.Swagger
import io.vertx.core.{Future => VxFuture}

import scala.collection.JavaConverters._

case class DropSecurityDefinitionsPreProcessorConfig()

class DropSecurityDefinitionsPreProcessor extends ScalaServiceVerticle with OpenApiPreProcessor with ConfigDecoder {
  var cfg: DropSecurityDefinitionsPreProcessorConfig = _

  override def vertxServiceAddressPrefixS: Option[String] = super.vertxServiceAddressPrefixS.orElse(Some(verticleId()))

  override def initService(): Unit = {
    cfg = decodeConfigUnsafe[DropSecurityDefinitionsPreProcessorConfig]
  }

  override def preProcess(openapi: Swagger): VxFuture[Swagger] = {
    Option(openapi.getSecurityDefinitions).map(_.clear())
    openapi.getPaths.asScala.values
      .foreach(_.getOperations.forEach(_.setSecurity(new util.ArrayList())))
    VxFuture.succeededFuture(openapi)
  }
}
