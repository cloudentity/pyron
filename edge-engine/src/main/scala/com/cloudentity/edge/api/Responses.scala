package com.cloudentity.edge.api
import io.circe.generic.semiauto._
import io.circe.syntax._
import com.cloudentity.edge.domain.http._
import io.vertx.core.buffer.Buffer

object Responses {
  case class Error(httpCode: Int, body: ErrorBody) {
    def toApiResponse() = ApiResponse(httpCode, Buffer.buffer(mkString(body)), Headers.of("Content-Type" -> "application/json"))
    def toApiResponse(headers: Headers) = ApiResponse(httpCode, Buffer.buffer(mkString(body)), headers.set("Content-Type", "application/json"))
    def toApiResponse(cookies: Option[ClientCookies]) = ApiResponse(httpCode, Buffer.buffer(mkString(body)), Headers.of("Content-Type" -> "application/json"), cookies)
  }
  case class ErrorBody(code: String, message: String)

  val errorBodyEncoder = deriveEncoder[ErrorBody]

  def mkString(body: ErrorBody): String =
    body.asJson(errorBodyEncoder).toString

  object Errors {
    val invalidRequest                = Error(400, ErrorBody("Request.Invalid", "The request could not be understood by the server due to malformed syntax."))
    val requiredIdentifierNotFound    = Error(400, ErrorBody("REQUIRED_IDENTIFIER_NOT_FOUND","Some of required identifiers could not be found"))
    val unauthenticated               = Error(401, ErrorBody("Authentication.Unauthenticated", "Not authenticated. This API is only available for authenticated users."))
    val hmacMismatch                  = Error(401, ErrorBody("Authentication.Mismatch", "Provided hmac request does not match the original request."))
    val hmacRequestOutdated           = Error(401, ErrorBody("Authentication.RequestOutdated", "The request was outdated."))
    val ruleNotFound                  = Error(404, ErrorBody("NO_RULE","No matching rule found"))
    val invalidCSRFHeader             = Error(403, ErrorBody("INVALID_CSRF", "CSRF header is invalid"))
    val targetUnreachable             = Error(500, ErrorBody("TARGET_SERVICE_UNREACHABLE", "Could not call target service"))
    val responseTimeout               = Error(504, ErrorBody("Response.Timeout", "Call to target service timed-out"))
    val systemTimeout                 = Error(504, ErrorBody("System.Timeout", "Request processing timed-out"))
    val authzUnreachable              = Error(500, ErrorBody("AUTHZ_SERVICE_UNREACHABLE", "Could not call Authz service"))
    val authzUnexpected               = Error(500, ErrorBody("AUTHZ_SERVICE_UNEXPECTED_CODE", "Authz service returned unexpected HTTP status code"))
    val unexpected                    = Error(500, ErrorBody("UNEXPECTED_ERROR","Sorry, unexpected error occurred"))

    def invalidRequest(msg: String): Error = {
      Error(400, ErrorBody("Request.Invalid", msg))
    }
  }
}