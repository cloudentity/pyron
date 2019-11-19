package com.cloudentity.pyron.domain.openapi

import com.cloudentity.pyron.domain.flow._
import com.cloudentity.pyron.domain.http.RelativeUri
import io.vertx.core.http.HttpMethod

case class OpenApiRule(method: HttpMethod, serviceId: ServiceId, group: GroupMatchCriteria, pathPattern: PathPattern, pathPrefix: PathPrefix,
                       dropPathPrefix: Boolean, rewriteMethod: Option[RewriteMethod], rewritePath: Option[RewritePath], plugins: List[PluginConf], tags: List[String], operationId: Option[String]) {
  lazy val targetServicePath: String =
    rewritePath.map(_.value).getOrElse {
      if (dropPathPrefix) pathPattern.value
      else pathPrefix.value + pathPattern.value
    }

  lazy val apiGwPath: String =
    group.basePath.map(_.value).getOrElse("") + pathPrefix.value + pathPattern.value
}

sealed trait ServiceId
case class StaticServiceId(host: TargetHost, port: Int, ssl: Boolean) extends ServiceId {
  override def toString(): String = s"${host.value}?port=${port}&ssl=${ssl}"
}
case class DiscoverableServiceId(name: ServiceClientName) extends ServiceId {
  override def toString: String = name.value
}

case class OpenApiConf(defaultSource: Option[SourceConf], defaultConverter: Option[ConverterConf], services: Option[Map[ServiceId, OpenApiServiceConf]])
case class OpenApiServiceConf(source: Option[SourceConf], converter: Option[ConverterConf])

case class SourceConf(path: RelativeUri)
case class ConverterConf(defaults: Option[OpenApiDefaultsConf], processors: Option[ProcessorsConf])
case class ProcessorsConf(pre: Option[List[String]], post: Option[List[String]])
case class OpenApiDefaultsConf(host: Option[Host], basePath: Option[BasePath], ssl: Option[Boolean])

case class BasePath(value: String) extends AnyVal
case class Host(value: String) extends AnyVal

object OpenApi {
  type OpenApiOperations = Map[io.swagger.models.HttpMethod, io.swagger.models.Operation]
}
