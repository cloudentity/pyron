package com.cloudentity.pyron.util

import org.mockserver.integration.ClientAndServer
import org.mockserver.matchers.MatchType
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.mockserver.model.JsonBody._
import org.mockserver.verify.VerificationTimes

trait MockUtils {
  def mockOnPathWithPongingBodyAndHeaders(service: ClientAndServer)(path: String, code: Int): Unit =
    service.when(request()).respond { request: HttpRequest =>
      if (request.getPath.getValue == path)
        response()
          .withStatusCode(code)
          .withBody(request.getBodyAsString)
          .withHeaders(request.getHeaders)
      else response().withStatusCode(404)
    }

  def mockOnPath(service: ClientAndServer)(path: String, resp: HttpResponse): Unit =
    service.when(request().withPath(path)).respond(resp)

  def mockOnPathWithBody(service: ClientAndServer)(path: String, body: String, resp: HttpResponse): Unit =
    service.when(request()
      .withPath(path)
      .withBody(
        json(body,
          MatchType.STRICT)
      )
    ).respond { request: HttpRequest =>
      if (request.getPath.getValue == path ) resp
      else response().withStatusCode(404)
    }

  def verifyRequestWithBody(service: ClientAndServer)(path: String, body: String): Unit =
    service.verify(
      request()
      .withPath(path)
      .withBody(
        json(body,
          MatchType.STRICT)
      ),
      VerificationTimes.once()
    )

  def verifyRequestNotInvoked(service: ClientAndServer)(path: String, body: String): Unit =
    service.verify(
      request()
      .withPath(path)
      .withBody(
        json(body,
          MatchType.STRICT)
      ),
      VerificationTimes.exactly(0)
    )

  def mockVaultServiceResponse(vaultService: ClientAndServer)(serial: String, body: String): Unit = {
    vaultService.when(HttpRequest.request().withMethod("GET").withPath(s"/v1/pki/cert/$serial"))
      .respond(HttpResponse.response(body).withStatusCode(200))
  }
}
