package com.cloudentity.pyron.domain.flow

import io.circe.Json

case class PluginName(value: String) extends AnyVal
case class PluginConf(name: PluginName, conf: Json, applyIf: Option[Json])

case class PluginAddressPrefix(value: String) extends AnyVal
case class ApiGroupPluginConf(name: PluginName,
                              conf: Json,
                              applyIf: Option[Json],
                              addressPrefixOpt: Option[PluginAddressPrefix])

trait PluginsConf {
  def pre: List[ApiGroupPluginConf]

  def endpoint: List[ApiGroupPluginConf]

  def post: List[ApiGroupPluginConf]

  def toList: List[ApiGroupPluginConf] = pre ::: endpoint ::: post
}

