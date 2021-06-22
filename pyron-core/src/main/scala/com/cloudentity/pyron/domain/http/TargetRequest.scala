package com.cloudentity.pyron.domain.http

import com.cloudentity.pyron.domain.flow.{PathParams, TargetService}
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.{HttpHeaders, HttpMethod}

import scala.collection.JavaConverters._

case class TargetRequest(method: HttpMethod,
                         service: TargetService,
                         uri: RelativeUri,
                         headers: Headers,
                         bodyOpt: Option[Buffer]) {

  def modifyHeaders(f: Headers => Headers): TargetRequest =
    this.copy(headers = f(headers))

  def modifyPathParams(f: PathParams => PathParams): TargetRequest =
    this.copy(uri = uri.modifyPathParams(f))

  def modifyQueryParams(f: QueryParams => QueryParams): TargetRequest =
    this.copy(uri = uri.modifyQuery(f))

  def removeHeader(name: String): TargetRequest =
    this.copy(headers = this.headers.remove(name))

  def withBearerAuth(token: String): TargetRequest = this.copy(headers =
    this.headers.set(HttpHeaders.AUTHORIZATION.toString, s"Bearer $token"))

  def withBody(bodyOpt: Option[Buffer]): TargetRequest =
    this.copy(bodyOpt = bodyOpt)

  def withHeader(name: String, value: String): TargetRequest =
    this.copy(headers = this.headers.set(name, value))

  def withHeaders(values: java.util.Map[String, String]): TargetRequest =
    withHeaders(values.asScala.toMap)

  def withHeaders(values: Map[String, String]): TargetRequest = this.copy(headers =
    values.foldLeft(this.headers) { case (hs, (key, value)) => hs.set(key, value) } )

  def withHeaderValues(values: java.util.Map[String, java.util.List[String]]): TargetRequest =
    withHeaderValues(values.asScala.toMap.mapValues(_.asScala.toList))

  def withHeaderValues(values: Map[String, List[String]]): TargetRequest = this.copy(headers =
    values.foldLeft(this.headers) { case (hs, (key, values)) => hs.setValues(key, values) } )

  def withMethod(method: HttpMethod): TargetRequest =
    this.copy(method = method)

  def withTargetService(service: TargetService): TargetRequest =
    this.copy(service = service)

  def withUri(uri: RelativeUri): TargetRequest =
    this.copy(uri = uri)

}
