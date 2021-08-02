package com.cloudentity.pyron.plugin.config

import com.cloudentity.pyron.domain.flow.ApiGroupPluginConf

case class ValidateRequest(conf: ApiGroupPluginConf)

sealed trait ValidateResponse {
  def koMsg: Option[String] =
    this match {
      case ValidateOk           => None
      case ValidateFailure(msg) => Some(msg)
      case ValidateError(msg)   => Some(msg)
    }
}
  case object ValidateOk extends ValidateResponse
  case class ValidateFailure(msg: String) extends ValidateResponse
  case class ValidateError(msg: String) extends ValidateResponse

object ValidateResponse {
  def ok(): ValidateResponse                 = ValidateOk
  def failure(msg: String): ValidateResponse = ValidateFailure(msg)
  def error(msg: String): ValidateResponse   = ValidateError(msg)
}