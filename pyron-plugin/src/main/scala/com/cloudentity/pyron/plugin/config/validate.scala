package com.cloudentity.pyron.plugin.config

import com.cloudentity.pyron.domain.flow.ApiGroupPluginConf

case class ValidateRequest(conf: ApiGroupPluginConf)

sealed trait ValidateResponse
  case object ValidateOk extends ValidateResponse
  case class ValidateFailure(msg: String) extends ValidateResponse
  case class ValidateError(msg: String) extends ValidateResponse

object ValidateResponse {
  def ok(): ValidateResponse                 = ValidateOk
  def failure(msg: String): ValidateResponse = ValidateFailure(msg)
  def error(msg: String): ValidateResponse   = ValidateError(msg)
}