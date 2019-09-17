package com.cloudentity.edge.accesslog

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

object AccessLogHelper {
  case class AccessLogConf(typeConf: AccessLogTypeConf, maskFields: Option[MaskFieldsConf])

  sealed trait AccessLogTypeConf
  case class LogAllFields(value: Boolean) extends AccessLogTypeConf
  case class LogWhitelistedFields(value: List[String]) extends AccessLogTypeConf

  object AccessLogConf {
    case class AccessLogConfRaw(all: Option[Boolean], whitelist: Option[List[String]], maskFields: Option[MaskFieldsConf])

    implicit val decoder: Decoder[AccessLogConf] = deriveDecoder[AccessLogConfRaw].emap {
      case AccessLogConfRaw(Some(all), None, maskFieldsConf) => Right(AccessLogConf(LogAllFields(all), maskFieldsConf))
      case AccessLogConfRaw(None, Some(whitelist), maskFieldsConf) => Right(AccessLogConf(LogWhitelistedFields(whitelist), maskFieldsConf))
      case AccessLogConfRaw(_, _, _) => Left("One of 'all' and 'whitelist' should be set")
    }
  }

  case class MaskFieldsConf(whole: Option[List[String]], partial: Option[List[String]])
  implicit val maskFieldsConfDecoder: Decoder[MaskFieldsConf] = deriveDecoder[MaskFieldsConf]
}

trait AccessLogHelper {
  def maskPartially(value: String): String = {
    val size = scala.math.ceil(value.length / 3.0).toInt
    s"***${value.substring(value.length - size)}"
  }
}