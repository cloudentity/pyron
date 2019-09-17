package com.cloudentity.edge.service.authz

import io.circe.parser.decode
import com.cloudentity.edge.commons.ClientWithTracing
import com.cloudentity.tools.vertx.http.SmartHttpClient
import com.cloudentity.tools.vertx.http.builder.SmartHttpResponse
import com.cloudentity.tools.vertx.scala.FutureConversions
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.buffer.Buffer
import io.vertx.core.{Future => VxFuture}
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import scalaz.{-\/, \/, \/-}
import io.circe._
import io.circe.generic.semiauto._
import com.cloudentity.edge.domain.authn.CloudentityAuthn
import com.cloudentity.edge.domain.flow.RequestCtx
import com.cloudentity.edge.service.authz.SecuritySidecarClient._

object SecuritySidecarClient {

  case class SecuritySidecarOk()
  sealed trait SecuritySidecarError
    case class SecuritySidecarFailure(reason: SecuritySidecarFailureReason) extends SecuritySidecarError
    sealed trait SecuritySidecarFailureReason
      case class NotAuthorized(recovery: Option[List[Recovery]]) extends SecuritySidecarFailureReason
      case class OtherReason(httpCode: Int, body: Buffer) extends SecuritySidecarFailureReason
    case class SecuritySidecarHttpError(err: Throwable) extends SecuritySidecarError
}

case class SidecarFailures(validator: String, message: String)
object SidecarFailures {
  implicit val sidecarFailuresDecoder: Decoder[SidecarFailures] = deriveDecoder
}

case class SidecarResponse(result: String, failures: Option[List[SidecarFailures]], recovery: Option[List[Recovery]])
object SidecarResponse {
  implicit val sidecarResponseDecoder: Decoder[SidecarResponse] = deriveDecoder
}
case class ValidationRequest(url: String, headers: List[Header], method: String, pathParams: Option[List[Param]], queryParams: Option[List[Param]])
object ValidationRequest {
  implicit val validationRequestEncoder: Encoder[ValidationRequest] = deriveEncoder
}

case class SidecarRequest(token: Option[String], policyName: String, request: ValidationRequest)
object SidecarRequest {
  implicit val sidecarRequestEncoder: Encoder[SidecarRequest] = deriveEncoder
}

case class Header(name: String, value: String)
object Header {
  implicit val headerEncoder: Encoder[Header] = deriveEncoder
}

case class Param(name: String, value: String)
object Param {
  implicit val paramEncoder: Encoder[Param] = deriveEncoder
}

trait SecuritySidecarCall extends FutureConversions with ClientWithTracing with CloudentityAuthn {
  def log: LoggingWithTracing

  def makeSecuritySidecarCall(client: SmartHttpClient)(ctx: TracingContext, req: RequestCtx, policyName: String)(implicit ec: VertxExecutionContext): VxFuture[SecuritySidecarError \/ SecuritySidecarOk] = {
    val path = "/v2/validatePolicy"
    val headers: List[Header] = req.request.headers.toMap.map { case (name, values) => Header(name, values.mkString(",")) }.toList
    val pathParams: List[Param] = req.request.uri.pathParams.value.map {case (name, value) => Param(name, value)}.toList
    val queryParams: List[Param] = req.request.uri.query.toMap.map { case (name, values) => Param(name, values.mkString(",")) }.toList

    val body = SidecarRequest.sidecarRequestEncoder.apply(
      SidecarRequest(
        token = req.authnCtx.asCloudentity.token,
        policyName = policyName,
        request = ValidationRequest(req.request.uri.value, headers, req.request.method.toString, Some(pathParams), Some(queryParams))
      )).noSpaces

    client.post(path)
      .withTracing(ctx)
      .endWithBody(ctx, body)
      .toScala().map(successHandler(ctx))
      .recover { case ex: Throwable => -\/(SecuritySidecarHttpError(ex)) }.toJava()
  }

  private def successHandler(ctx: TracingContext)(response: SmartHttpResponse): SecuritySidecarError \/ SecuritySidecarOk = {
    log.debug(ctx, s"Received ${response.getHttp.statusCode()} status code from security sidecar")
    val body = response.getBody()
    response.getHttp.statusCode() match {
      case 200 => {
        decode[SidecarResponse](body.toString) match {
          case Right(resp) => resp.result match {
            case "NOT_AUTHORIZED" =>
              -\/(SecuritySidecarFailure(NotAuthorized(resp.recovery)))
            case "AUTHORIZED" =>
              \/-(SecuritySidecarOk())
            case _ =>
              log.debug(ctx, s"Sidecar returned unexpected response: {}", body)
              -\/(SecuritySidecarFailure(OtherReason(200, body)))
          }
          case Left(ex) => {
            log.debug(ctx, s"could not decode security sidecar body. ${ex.getMessage}")
            -\/(SecuritySidecarFailure(OtherReason(200, body)))
          }
        }
      }
      case code => {
        log.debug(ctx, s"Sidecar returned unexpected HTTP code: $code, body: {}", body)
        -\/(SecuritySidecarFailure(OtherReason(code, body)))
      }
    }
  }
}