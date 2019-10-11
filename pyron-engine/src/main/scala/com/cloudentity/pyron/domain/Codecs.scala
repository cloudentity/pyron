package com.cloudentity.pyron.domain

import java.time.Duration

import com.cloudentity.pyron.domain.flow._
import com.cloudentity.pyron.domain.http._
import com.cloudentity.pyron.domain.rule.{RequestPluginsConf, ResponsePluginsConf, RuleConf, RuleConfWithPlugins}
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.circe.CursorOp.DownField
import io.circe.Decoder.Result
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, ObjectEncoder, _}
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.{JsonObject => VxJsonObject}

import scala.concurrent.Future
import scala.util.Try
import scala.util.matching.Regex

object Codecs {
  def IdEnc[A](f: A => String): Encoder[A] = Encoder.encodeString.contramap(f)
  def IdDec[A](f: String => A): Decoder[A] = Decoder.decodeString.map(f)

  implicit val durationDecoder = Decoder.decodeString.emapTry(value => Try(Duration.parse(value)))

  implicit lazy val httpMethodEnc: Encoder[HttpMethod] = Encoder.encodeString.contramap(_.toString)
  implicit lazy val httpMethodDec: Decoder[HttpMethod] = (c: HCursor) => c.focus
    .flatMap(_.asString)
    .map(method => Try(HttpMethod.valueOf(method.toUpperCase))) match {
    case Some(util.Success(method)) => Right(method)
    case Some(util.Failure(ex))     => Left(DecodingFailure.fromThrowable(ex, c.history))
    case None => Left(DecodingFailure("HttpMethod should be String", c.history))
  }
  // encodes T to json String
  def AnyValEncoder[T](encode: T => String): Encoder[T] =
    Encoder.encodeString.contramap[T](encode)

  // decodes T from json String
  def AnyValDecoder[T](decode: String => T): Decoder[T] =
    Decoder.decodeString.map(decode)

  implicit lazy val rewriteMethodEnc: Encoder[RewriteMethod] = AnyValEncoder(_.value.name())
  implicit lazy val rewriteMethodDec: Decoder[RewriteMethod] = Decoder.decodeString.map(_.toUpperCase).emap {
    case "CONNECT" => Right(HttpMethod.CONNECT).map(RewriteMethod)
    case "DELETE"  => Right(HttpMethod.DELETE).map(RewriteMethod)
    case "GET"     => Right(HttpMethod.GET).map(RewriteMethod)
    case "HEAD"    => Right(HttpMethod.HEAD).map(RewriteMethod)
    case "OPTIONS" => Right(HttpMethod.OPTIONS).map(RewriteMethod)
    case "OTHER"   => Right(HttpMethod.OTHER).map(RewriteMethod)
    case "PATCH"   => Right(HttpMethod.PATCH).map(RewriteMethod)
    case "POST"    => Right(HttpMethod.POST).map(RewriteMethod)
    case "PUT"     => Right(HttpMethod.PUT).map(RewriteMethod)
    case "TRACE"   => Right(HttpMethod.TRACE).map(RewriteMethod)
    case x         => Left(s"Unsupported HTTP method: $x")
  }

  implicit lazy val rewritePathEnc: Encoder[RewritePath] = AnyValEncoder(_.value)
  implicit lazy val rewritePathDec: Decoder[RewritePath] = AnyValDecoder(RewritePath)

  implicit lazy val pathPatternEnc: Encoder[PathPattern] = AnyValEncoder(_.value)
  implicit lazy val pathPatternDec: Decoder[PathPattern] = AnyValDecoder(PathPattern)

  implicit lazy val pathPrefixEnc: Encoder[PathPrefix] = AnyValEncoder(_.value)
  implicit lazy val pathPrefixDec: Decoder[PathPrefix] = AnyValDecoder(PathPrefix)

  implicit lazy val relativeUriEnc: Encoder[RelativeUri] = Encoder.encodeString.contramap(_.value)
  implicit lazy val relativeUriDec: Decoder[RelativeUri] = Decoder.decodeString.emapTry(RelativeUri.of)

  implicit lazy val targetHostEnc: Encoder[TargetHost] = AnyValEncoder(_.value)
  implicit lazy val targetHostDec: Decoder[TargetHost] = AnyValDecoder(TargetHost)

  implicit lazy val serviceTagEnc: Encoder[ServiceClientName] = AnyValEncoder(_.value)
  implicit lazy val serviceTagDec: Decoder[ServiceClientName] = AnyValDecoder(ServiceClientName)

  implicit lazy val pluginNameEnc: Encoder[PluginName] = AnyValEncoder(_.value)
  implicit lazy val pluginNameDec: Decoder[PluginName] = AnyValDecoder(PluginName)

  implicit lazy val PathParamNameEnc: Encoder[PathParamName] = AnyValEncoder(_.value)
  implicit lazy val PathParamNameDec: Decoder[PathParamName] = AnyValDecoder(PathParamName)

  implicit lazy val pluginConfEnc: Encoder[PluginConf] = deriveEncoder
  implicit lazy val pluginConfDec: Decoder[PluginConf] = new Decoder[PluginConf] {
    override def apply(c: HCursor): Result[PluginConf] =
      Decoder.decodeJsonObject.apply(c).flatMap { obj =>
        obj.apply("name").flatMap(_.asString) match {
          case Some(name) =>
            val conf = obj.apply("conf").getOrElse(Json.obj())
            Right(PluginConf(PluginName(name), conf))
          case None =>
            Left(DecodingFailure("Missing 'name' attribute", DownField("name") :: c.history))
        }
      }
  }

  implicit lazy val bufferEnc: Encoder[Buffer] = Encoder.encodeString.contramap(_.toString)
  implicit lazy val bufferDec: Decoder[Buffer] = Decoder.decodeString.map(Buffer.buffer)

  implicit lazy val headersEnc: Encoder[Headers] = (a: Headers) => a.toMap.asJson
  implicit lazy val headersDec: Decoder[Headers] = Decoder.decodeJson.emap(_.as[Map[String, List[String]]].left.map(_.message)).map(Headers.apply)

  implicit lazy val apiResponseEnc: Encoder[ApiResponse] = deriveEncoder
  implicit lazy val apiResponseDec: Decoder[ApiResponse] = deriveDecoder

  implicit lazy val modifyResponseEnc: Encoder[List[ApiResponse => Future[ApiResponse]]] = Encoder.encodeString.contramap(_ => "modify_response_functions")
  implicit lazy val modifyResponseDec: Decoder[List[ApiResponse => Future[ApiResponse]]] = Decoder.decodeString.map(_ => Nil)

  implicit lazy val propertiesEnc: Encoder[Properties] = Encoder.encodeString.contramap(_ => "properties")
  implicit lazy val propertiesDec: Decoder[Properties] = Decoder.decodeString.map(_ => Properties())

  implicit lazy val correlationCtxEnc: Encoder[CorrelationCtx] = deriveEncoder
  lazy val strictCorrelationCtxDec: Decoder[CorrelationCtx] = deriveDecoder
  implicit lazy val correlationCtxDec: Decoder[CorrelationCtx] = (c: HCursor) =>
    c.focus match {
      case Some(json) => strictCorrelationCtxDec.decodeJson(json)
      case None       => Right(CorrelationCtx("", FlowId(""), Map()))
    }

  implicit lazy val tracingCtxEnc: Encoder[TracingContext] = Encoder.encodeString.contramap(_.getTraceId)
  implicit lazy val tracingCtxDec: Decoder[TracingContext] = Decoder.decodeString.map(_ => TracingContext.dummy())

  implicit lazy val authnCtxEnc: Encoder[AuthnCtx] = Encoder.encodeJsonObject.contramap(ctx => JsonObject.fromMap(ctx.value))
  implicit lazy val authnCtxDec: Decoder[AuthnCtx] = Decoder.decodeJsonObject.map(o => AuthnCtx(o.toMap))

  implicit lazy val requestCtxEnc: Encoder[RequestCtx] = deriveEncoder
  implicit lazy val requestCtxDec: Decoder[RequestCtx] = deriveDecoder

  implicit lazy val responseCtxEnc: Encoder[ResponseCtx] = deriveEncoder
  implicit lazy val responseCtxDec: Decoder[ResponseCtx] = deriveDecoder

  implicit lazy val QueryParamsEnc: Encoder[QueryParams] = Encoder.encodeString.contramap(_.toString)
  implicit lazy val QueryParamsDec: Decoder[QueryParams] = Decoder.decodeString.emapTry(QueryParams.fromString)

  implicit lazy val OriginalRequestEnc: Encoder[OriginalRequest] = deriveEncoder
  implicit lazy val OriginalRequestDec: Decoder[OriginalRequest] = deriveDecoder

  implicit lazy val targetRequestEnc: Encoder[TargetRequest] = deriveEncoder
  implicit lazy val targetRequestDec: Decoder[TargetRequest] = deriveDecoder

  implicit lazy val jsonObjectEnc: Encoder[JsonObject] = (a: JsonObject) => Json.fromJsonObject(a)
  implicit lazy val jsonObjectDec: Decoder[JsonObject] = Decoder.decodeJsonObject

  implicit lazy val vxJsonObjectEnc: Encoder[VxJsonObject] =
    (a: VxJsonObject) => jsonObjectEnc(decode[JsonObject](a.toString).getOrElse(throw new Exception("never fails - converting between JSON objects")))
  implicit lazy val vxJsonObjectDec: Decoder[VxJsonObject] = Decoder.decodeJsonObject.map(o => new VxJsonObject(o.asJson.noSpaces))

  implicit lazy val regexEnc: Encoder[Regex] = Encoder.encodeString.contramap(_.regex)
  implicit lazy val regexDec: Decoder[Regex] = Decoder.decodeString.map(_.r)

  implicit lazy val PathMatchingEnc: Encoder[PathMatching] = deriveEncoder
  implicit lazy val PathMatchingDec: Decoder[PathMatching] = deriveDecoder

  implicit lazy val matchCriteriaEnc: Encoder[EndpointMatchCriteria] = deriveEncoder
  implicit lazy val matchCriteriaDec: Decoder[EndpointMatchCriteria] = deriveDecoder

  implicit lazy val targetServiceEnc: Encoder[TargetService] = deriveEncoder
  implicit lazy val targetServiceDec: Decoder[TargetService] = deriveDecoder

  implicit lazy val targetServiceRuleEnc: Encoder[TargetServiceRule] = deriveEncoder
  implicit lazy val targetServiceRuleDec: Decoder[TargetServiceRule] = deriveDecoder

  implicit lazy val CallOptsEnc: Encoder[CallOpts] = deriveEncoder
  implicit lazy val CallOptsDec: Decoder[CallOpts] = deriveDecoder

  implicit lazy val ruleConfEnc: Encoder[RuleConf] = deriveEncoder
  implicit lazy val ruleConfDec: Decoder[RuleConf] = deriveDecoder

  implicit lazy val ruleConfWithPluginsEnc: Encoder[RuleConfWithPlugins] = deriveEncoder
  implicit lazy val ruleConfWithPluginsDec: Decoder[RuleConfWithPlugins] = deriveDecoder

  implicit lazy val requestPluginsConfEnc: Encoder[RequestPluginsConf] = deriveEncoder
  implicit lazy val requestPluginsConfDec: Decoder[RequestPluginsConf] = deriveDecoder

  implicit lazy val responsePluginsConfEnc: Encoder[ResponsePluginsConf] = deriveEncoder
  implicit lazy val responsePluginsConfDec: Decoder[ResponsePluginsConf] = deriveDecoder

  implicit lazy val BasePathDecoder: Decoder[BasePath] = Decoder.decodeString.map(BasePath.apply)
  implicit lazy val DomainPatternDecoder: Decoder[DomainPattern] = Decoder.decodeString.map(DomainPattern.apply)
  implicit lazy val GroupMatchCriteriaDecoder: Decoder[GroupMatchCriteria] = deriveDecoder
}