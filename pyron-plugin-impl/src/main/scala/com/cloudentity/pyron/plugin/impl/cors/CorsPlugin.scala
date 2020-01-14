package com.cloudentity.pyron.plugin.impl.cors

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import com.cloudentity.pyron.plugin.config._
import com.cloudentity.pyron.domain.flow._
import com.cloudentity.pyron.domain.http.ApiResponse
import com.cloudentity.pyron.domain.rule.{RequestPluginsConf, ResponsePluginsConf, RuleConfWithPlugins}
import com.cloudentity.pyron.plugin.ExtendRules
import com.cloudentity.pyron.plugin.verticle.RequestResponsePluginVerticle
import com.cloudentity.pyron.util.ConfigDecoder
import com.cloudentity.tools.vertx.http.Headers
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod

import scala.concurrent.Future

case class CorsPluginConf(
  allowCredentials: Option[Boolean],        // Access-Control-Allow-Credentials
  allowedHttpHeaders: Option[List[String]], // Access-Control-Allow-Headers
  allowedHttpMethods: Option[List[String]], // Access-Control-Allow-Methods
  allowedOrigins: Option[List[String]],     // Access-Control-Allow-Origin
  preflightMaxAgeInSeconds: Option[Long]    // Access-Control-Max-Age
) {
  /**
   * Creates CorsSettings using defaults values if None.
   */
  def withDefaults: CorsSettings = CorsSettings(
    allowCredentials         = allowCredentials.getOrElse(CorsPluginConf.default.allowCredentials),
    allowedHttpHeaders       = allowedHttpHeaders.getOrElse(CorsPluginConf.default.allowedHttpHeaders),
    allowedHttpMethods       = allowedHttpMethods.getOrElse(CorsPluginConf.default.allowedHttpMethods),
    allowedOrigins           = allowedOrigins.getOrElse(CorsPluginConf.default.allowedOrigins),
    preflightMaxAgeInSeconds = preflightMaxAgeInSeconds.getOrElse(CorsPluginConf.default.preflightMaxAgeInSeconds)
  )

  /**
   * Creates a new CorsPluginConf and takes values from the other if in this it equals None.
   */
  def mergeWith(other: CorsPluginConf): CorsPluginConf = CorsPluginConf(
    allowCredentials         = allowCredentials.orElse(other.allowCredentials),
    allowedHttpHeaders       = allowedHttpHeaders.orElse(other.allowedHttpHeaders),
    allowedHttpMethods       = allowedHttpMethods.orElse(other.allowedHttpMethods),
    allowedOrigins           = allowedOrigins.orElse(other.allowedOrigins),
    preflightMaxAgeInSeconds = preflightMaxAgeInSeconds.orElse(other.preflightMaxAgeInSeconds)
  )
}

object CorsPluginConf {
  val default = CorsSettings(
    allowCredentials         = true,
    allowedHttpHeaders       = List("*"),
    allowedHttpMethods       = List("*"),
    allowedOrigins           = List("*"),
    preflightMaxAgeInSeconds = 600
  )
}

case class CorsSettings(
   allowCredentials: Boolean,
   allowedHttpHeaders: List[String],
   allowedHttpMethods: List[String],
   allowedOrigins: List[String],
   preflightMaxAgeInSeconds: Long
 ) {
  def toHeaders(origin: String): Map[String, String] = Map(
    "Access-Control-Allow-Origin"      -> getAllowedOrigin(origin),
    "Access-Control-Allow-Credentials" -> (if (allowCredentials) "true" else ""),
    "Access-Control-Allow-Headers"     -> allowedHttpHeaders.mkString(","),
    "Access-Control-Allow-Methods"     -> allowedHttpMethods.mkString(","),
    "Access-Control-Max-Age"           -> preflightMaxAgeInSeconds.toString
  ).filter(_._2.nonEmpty)

  def getAllowedOrigin(origin: String): String =
    if (allowedOrigins.contains("*")) origin
    else allowedOrigins.find(_ == origin).getOrElse(allowedOrigins.mkString(","))
}

/**
 * CorsPlugin is a request/response plugin which enables CORS.
 *
 * It automatically adds OPTIONS rules for each defined GET, POST, PUT, DELETE rule,
 * and injects Access-Control headers as as postFlow response plugin.
 */
class CorsPlugin extends RequestResponsePluginVerticle[CorsPluginConf] with ConfigDecoder {
  var verticleCorsPluginConf: CorsPluginConf = _

  override def initService(): Unit = {
    verticleCorsPluginConf = decodeConfigUnsafe[CorsPluginConf](confDecoder)
  }

  override def name = PluginName("cors")
  override def validate(conf: CorsPluginConf): ValidateResponse = ValidateOk
  override def confDecoder: Decoder[CorsPluginConf] = deriveDecoder
  implicit val confEncoder: Encoder[CorsPluginConf] = deriveEncoder

  /**
   * As a response plugin injects Access-Control headers to the response.
   */
  override def apply(ctx: ResponseCtx, conf: CorsPluginConf): Future[ResponseCtx] = Future.successful {
    val corsHeaders = conf.mergeWith(verticleCorsPluginConf).withDefaults
      .toHeaders(ctx.request.headers.get("origin").getOrElse(""))
    ctx.modifyResponse(_.modifyHeaders(_.setHeaders(corsHeaders)))
  }

  /**
   * As a request plugin aborts processing with 200 OK status for OPTIONS requests.
   */
  override def apply(ctx: RequestCtx, conf: CorsPluginConf) = Future.successful {
    if (ctx.request.method == HttpMethod.OPTIONS) {
      ctx.abort(ApiResponse(200, Buffer.buffer(), Headers()))
    } else {
      ctx
    }
  }

  /**
   * ExtendRules adds a rule for method OPTIONS with cors as a request plugins for each rule.
   */
  override def extendRules(ruleConf: RuleConfWithPlugins, conf: CorsPluginConf): ExtendRules = {
    val ruleConfWithOptions = ruleConf.rule.copy(
      endpointName = ruleConf.rule.endpointName.map(_ + "-options"),
      criteria = ruleConf.rule.criteria.copy(method = HttpMethod.OPTIONS)
    )

    val preRequestPlugin = ApiGroupPluginConf(PluginName("cors"), confEncoder(conf))

    ExtendRules(append = List(RuleConfWithPlugins(
      rule = ruleConfWithOptions,
      requestPlugins = RequestPluginsConf(List(preRequestPlugin), Nil, Nil),
      responsePlugins = ResponsePluginsConf(Nil, Nil, Nil)
    )))
  }
}
