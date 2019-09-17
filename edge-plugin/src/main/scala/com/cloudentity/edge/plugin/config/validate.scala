package com.cloudentity.edge.plugin.config

import com.cloudentity.edge.domain.flow.PluginConf

case class ValidateRequest(conf: PluginConf)

sealed trait ValidateResponse
  case object ValidateOk extends ValidateResponse
  case class ValidateFailure(msg: String) extends ValidateResponse
  case class ValidateError(msg: String) extends ValidateResponse
