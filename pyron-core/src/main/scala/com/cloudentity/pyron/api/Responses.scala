package com.cloudentity.pyron.api

import io.circe.generic.semiauto._
import io.circe.syntax._
import com.cloudentity.pyron.domain.http._
import io.vertx.core.buffer.Buffer

object Responses {
  case class Error(httpCode: Int, body: ErrorBody) {
    def toApiResponse() = ApiResponse(httpCode, Buffer.buffer(mkString(body)), Headers.of("Content-Type" -> "application/json"))
    def toApiResponse(headers: Headers) = ApiResponse(httpCode, Buffer.buffer(mkString(body)), headers.set("Content-Type", "application/json"))
  }
  case class ErrorBody(code: String, message: String)

  val errorBodyEncoder = deriveEncoder[ErrorBody]

  def mkString(body: ErrorBody): String =
    body.asJson(errorBodyEncoder).toString

  object Errors {
    val unauthenticated               = Error(401, ErrorBody("Authentication.Unauthenticated", "Not authenticated. This API is only available for authenticated users."))
    val ruleNotFound                  = Error(404, ErrorBody("Rule.Missing","No matching rule found"))
    val targetUnreachable             = Error(500, ErrorBody("Target.Unreachable", "Could not call target service"))
    val responseTimeout               = Error(504, ErrorBody("Response.Timeout", "Call to target service timed-out"))
    val systemTimeout                 = Error(504, ErrorBody("System.Timeout", "Request processing timed-out"))
    val requestBodyTooLarge           = Error(413, ErrorBody("Rule.RequestBodyTooLarge", "Request body is too large"))
    val unexpected                    = Error(500, ErrorBody("System.Unexpected","Sorry, unexpected error occurred"))
  }
}