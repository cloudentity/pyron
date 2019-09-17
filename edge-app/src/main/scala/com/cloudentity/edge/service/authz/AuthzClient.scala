package com.cloudentity.edge.service.authz

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import com.cloudentity.edge.commons.ClientWithTracing
import com.cloudentity.edge.cookie.CookieUtils
import com.cloudentity.edge.domain.http.Headers
import com.cloudentity.edge.domain.flow.RequestCtx
import com.cloudentity.edge.jwt.{JwtService, JwtToken}
import com.cloudentity.edge.service.authz.AuthzClient._
import com.cloudentity.tools.vertx.http.SmartHttpClient
import com.cloudentity.tools.vertx.http.builder.{RequestCtxBuilder, SmartHttpResponse}
import com.cloudentity.tools.vertx.scala.FutureConversions
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.buffer.Buffer
import io.vertx.core.{Future => VxFuture}
import com.cloudentity.tools.vertx.scala.VertxExecutionContext

import scala.concurrent.Future
import scalaz.{-\/, \/, \/-}

object AuthzClient {
  case class AuthzOk()
  sealed trait AuthzError
    case class AuthzFailure(reason: AuthzFailureReason) extends AuthzError
      sealed trait AuthzFailureReason
        case class Unauthenticated(body: Buffer) extends AuthzFailureReason
        case class Unauthorized(body: Buffer) extends AuthzFailureReason
        case class OtherFailureReason(httpCode: Int, body: Buffer) extends AuthzFailureReason
    case class AuthzHttpError(err: Throwable) extends AuthzError
}

class AuthzClient(client: SmartHttpClient, jwtService: JwtService)(implicit ec: VertxExecutionContext) extends AuthzCall {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  def validate(ctx: TracingContext, jwt: JwtToken, policyName: String, reqOpt: Option[RequestCtx], applicationId: Option[String]): Future[AuthzError \/ AuthzOk] = {
    jwtService.sign(jwt).toScala().flatMap { auth =>
      log.debug(ctx, s"Calling Authz to validate `$policyName`, jwt='$jwt', applicationId=${applicationId.getOrElse("null")}")
      makeCall(client)(ctx, policyName, reqOpt, applicationId, Headers.of(Map("Authorization" -> s"Bearer $auth")))
    }
  }
}

trait AuthzCall extends FutureConversions with ClientWithTracing {
  def log: LoggingWithTracing

  def makeCall(client: SmartHttpClient)(ctx: TracingContext, policyName: String, reqOpt: Option[RequestCtx], applicationId: Option[String], headers: Headers)(implicit ec: VertxExecutionContext): Future[AuthzError \/ AuthzOk] = {
    val path =
      applicationId match {
        case Some(appId) => s"/policy/$policyName/application/$appId/validate"
        case None        => s"/policy/$policyName/validate"
      }

    val authzReq = headers.toMap.foldLeft(client.post(path)) { case (req, (key, values)) =>
      values.foldLeft(req) { case (r, value) => r.putHeader(key, value) }
    }

    val call: RequestCtxBuilder => VxFuture[SmartHttpResponse] =
      reqOpt match {
        case Some(req) =>
          val data = s"""{"request": ${RequestValidateData.toJson(req).value}}"""
          _.endWithBody(ctx, data)
        case None =>
          _.endWithBody(ctx)
      }

    call(authzReq.withTracing(ctx)).toScala()
      .map(successHandler(ctx))
      .recover { case ex: Throwable => -\/(AuthzHttpError(ex)) }
  }

  private def successHandler(ctx: TracingContext)(response: SmartHttpResponse): AuthzError \/ AuthzOk = {
    log.debug(ctx, s"Received ${response.getHttp.statusCode()} status code from Authz")
      val body = response.getBody
      response.getHttp.statusCode() match {
        case 200  => \/-(AuthzOk())
        case 401  => -\/(AuthzFailure(Unauthenticated(body)))
        case 403  => -\/(AuthzFailure(Unauthorized(body)))
        case code => -\/(AuthzFailure(OtherFailureReason(code, body)))
      }
    }
}

case class HeaderValue(value: Either[String, List[String]])
case class RequestValidateData(method: String, url: String, body: Option[Json], headers: Map[String, HeaderValue], pathParams: Map[String, String], queryParams: Map[String, String], cookies: Map[String, String])
case class MaybeValidJson(value: String) extends AnyVal

object RequestValidateData {

  implicit lazy val RequestValidateDataEnc: Encoder[RequestValidateData] = deriveEncoder
  implicit lazy val RequestValidateDataDec: Decoder[RequestValidateData] = deriveDecoder

  implicit lazy val HeaderValueEnc: Encoder[HeaderValue] =
    (a: HeaderValue) =>
      a.value match {
        case Left(v) => Encoder.encodeString.apply(v)
        case Right(vs) => Encoder.encodeList[String].apply(vs)
      }

  implicit lazy val HeaderValueDec: Decoder[HeaderValue] =
    Decoder.decodeString.map(v => HeaderValue(Left(v)))
      .or(Decoder.decodeList[String].map(v => HeaderValue(Right(v))))

  /**
    * Returns JSON representation of TargetRequest with RequestValidateData schema.
    * RequestValidateData.body is Option[Json] but we do not want to parse TargetRequest.body to JSON for the sake of performance.
    * We are not sure if TargetRequest.body is valid JSON, so this methods returns MaybeValidJson
    */
  def toJson(ctx: RequestCtx): MaybeValidJson = {
    def mapHeaders(headers: Headers): Map[String, HeaderValue] =
      headers.toMap.mapValues {
        case Nil      => HeaderValue(Right(Nil))
        case h :: Nil => HeaderValue(Left(h))
        case hs       => HeaderValue(Right(hs))
      }

    val woBody =
      RequestValidateData(
        method      = ctx.original.method.toString,
        url         = ctx.request.uri.value,
        body        = Some(Json.fromString("$ref:body")), // we do not want to parse TargetRequest.body to JSON, we replace "$ref:body" with TargetRequest.body to improve performance
        headers     = mapHeaders(ctx.original.headers),
        pathParams  = ctx.original.pathParams.value,
        queryParams = ctx.original.queryParams.toMap.flatMap { case (key, values) => values.headOption.map(key -> _) },
        cookies     = CookieUtils.parseCookies(ctx.original.headers.getValues("Cookie").getOrElse(Nil))
      ).asJson.noSpaces

    ctx.original.bodyOpt.flatMap(body => if (body.length() > 0) Some(body.toString()) else None) match {
      case Some(body) => MaybeValidJson(woBody.replace("\"$ref:body\"", body))
      case None       => MaybeValidJson(woBody.replace("\"$ref:body\"", "null"))
    }
  }
}
