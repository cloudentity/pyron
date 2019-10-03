package com.cloudentity.pyron.openapi

import io.circe.{Decoder, KeyDecoder, KeyEncoder}
import io.circe.generic.semiauto.deriveDecoder
import com.cloudentity.pyron.domain.Codecs.AnyValDecoder
import com.cloudentity.pyron.domain.Codecs._
import com.cloudentity.pyron.domain.flow.{ServiceClientName, TargetHost}
import com.cloudentity.pyron.domain.openapi.{BasePath, ConverterConf, DiscoverableServiceId, Host, OpenApiConf, OpenApiDefaultsConf, OpenApiServiceConf, ProcessorsConf, ServiceId, SourceConf, StaticServiceId}

import scala.util.Try

object Codecs {
  implicit lazy val basePathDecoder: Decoder[BasePath] = AnyValDecoder(BasePath)
  implicit lazy val hostDecoder: Decoder[Host] = AnyValDecoder(Host)

  implicit lazy val ServiceIdKeyDecoder = new KeyDecoder[ServiceId] {
    override def apply(key: String): Option[ServiceId] =
      key.split("\\$").toList match {
        case serviceName :: Nil  => Some(DiscoverableServiceId(ServiceClientName(serviceName)))
        case host :: port :: Nil => Try(StaticServiceId(TargetHost(host), port.toInt, false)).toOption
        case host :: port :: ssl :: Nil => Try(StaticServiceId(TargetHost(host), port.toInt, ssl.toBoolean)).toOption
        case _                   => None
      }
  }

  implicit lazy val ServiceIdEncoder = new KeyEncoder[ServiceId] {
    override def apply(key: ServiceId): String = key.toString
  }

  implicit lazy val openApiDefaultsConfDecoder = deriveDecoder[OpenApiDefaultsConf]
  implicit lazy val processorsConfDecoder = deriveDecoder[ProcessorsConf]
  implicit lazy val sourceConfDecoder = deriveDecoder[SourceConf]
  implicit lazy val converterConfDecoder = deriveDecoder[ConverterConf]
  implicit lazy val openApiServiceConfDecoder = deriveDecoder[OpenApiServiceConf]
  implicit lazy val openApiRouteConfDecoder = deriveDecoder[OpenApiConf]

}
