package com.cloudentity.pyron.domain.http

import io.vertx.core.buffer.Buffer

case class ApiResponse(statusCode: Int, body: Buffer, headers: Headers) {
  def modifyHeaders(f: Headers => Headers): ApiResponse =
    this.copy(headers = f(headers))

  def withStatusCode(statusCode: Int): ApiResponse =
    this.copy(statusCode = statusCode)

  def withBody(body: Buffer): ApiResponse =
    this.copy(body = body)
}

object ApiResponse {
  def create(statusCode: Int, body: Buffer): ApiResponse =
    ApiResponse(statusCode, body, Headers())

  def create(statusCode: Int, body: Buffer, headers: Headers): ApiResponse =
    ApiResponse(statusCode, body, headers)
}
