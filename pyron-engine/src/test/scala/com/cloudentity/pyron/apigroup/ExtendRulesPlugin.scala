package com.cloudentity.pyron.apigroup

import com.cloudentity.pyron.domain.flow.{PluginName, RequestCtx}
import com.cloudentity.pyron.domain.http.ApiResponse
import com.cloudentity.pyron.domain.rule.RuleConfWithPlugins
import com.cloudentity.pyron.plugin.ExtendRules
import com.cloudentity.pyron.plugin.config.{ValidateOk, ValidateResponse}
import com.cloudentity.pyron.plugin.verticle.RequestPluginVerticle
import com.cloudentity.tools.vertx.http.Headers
import io.circe.Decoder
import io.vertx.core.buffer.Buffer

import scala.concurrent.Future

class ExtendRulesPlugin extends RequestPluginVerticle[Unit] {
  override def name: PluginName = PluginName("extend")
  override def apply(ctx: RequestCtx, conf: Unit): Future[RequestCtx] = Future.successful(ctx.abort(ApiResponse(200, Buffer.buffer(), Headers())))
  override def validate(conf: Unit): ValidateResponse = ValidateOk
  override def confDecoder: Decoder[Unit] = Decoder.decodeUnit

  override def extendRules(ruleConf: RuleConfWithPlugins, conf: Unit): ExtendRules =
    ExtendRules(
      prepend = List(ruleConf.copy(rule = ruleConf.rule.copy(endpointName = Some("prepended")))),
      append = List(ruleConf.copy(rule = ruleConf.rule.copy(endpointName = Some("appended"))))
    )
}