package com.cloudentity.pyron.domain.http

import java.net.{URI, URLEncoder}
import com.cloudentity.pyron.domain.flow.{PathParams, RewritePath, TargetService}
import com.cloudentity.pyron.domain.http.Cookie.Cookies
import com.cloudentity.pyron.rule.PreparedPathRewrite.rewritePathWithPathParams
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.{HttpHeaders, HttpMethod}
import org.apache.http.client.utils.URLEncodedUtils

import java.nio.charset.Charset.defaultCharset
import scala.collection.JavaConverters._
import scala.util.Try

case class TargetRequest(method: HttpMethod,
                         service: TargetService,
                         uri: RelativeUri,
                         headers: Headers,
                         bodyOpt: Option[Buffer]) {

  def modifyHeaders(f: Headers => Headers): TargetRequest = this.copy(headers = f(headers))

  def withBearerAuth(token: String): TargetRequest = this.copy(
    headers = this.headers.set(HttpHeaders.AUTHORIZATION.toString, s"Bearer $token")
  )

  def withHeader(name: String, value: String): TargetRequest =
    this.copy(headers = this.headers.set(name, value))

  def withHeaders(values: Map[String, String]): TargetRequest =
    this.copy(headers =
      values.foldLeft(this.headers) { case (hs, (key, value)) =>
        hs.set(key, value)
      }
    )

  def withHeaders(values: java.util.Map[String, String]): TargetRequest =
    withHeaders(values.asScala.toMap)

  def withHeaderValues(values: Map[String, List[String]]): TargetRequest =
    this.copy(headers =
      values.foldLeft(this.headers) { case (hs, (key, values)) =>
        hs.setValues(key, values)
      }
    )

  def withHeaderValues(values: java.util.Map[String, java.util.List[String]]): TargetRequest =
    withHeaderValues(values.asScala.toMap.mapValues(_.asScala.toList))

  def removeHeader(name: String): TargetRequest =
    this.copy(headers = this.headers.remove(name))

  def withMethod(method: HttpMethod): TargetRequest =
    this.copy(method = method)

  def withTargetService(service: TargetService): TargetRequest =
    this.copy(service = service)

  def withUri(uri: RelativeUri): TargetRequest =
    this.copy(uri = uri)

  def withBody(bodyOpt: Option[Buffer]): TargetRequest =
    this.copy(bodyOpt = bodyOpt)

  def modifyPathParams(f: PathParams => PathParams): TargetRequest =
    this.copy(uri = uri.modifyPathParams(f))

  def modifyQueryParams(f: QueryParams => QueryParams): TargetRequest =
    this.copy(uri = uri.modifyQuery(f))
}

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

case class OriginalRequest(method: HttpMethod,
                           path: UriPath,
                           scheme: String,
                           host: String,
                           localHost: String,
                           remoteHost: String,
                           pathParams: PathParams,
                           queryParams: QueryParams,
                           headers: Headers,
                           cookies: Cookies,
                           bodyOpt: Option[Buffer])

object RelativeUri {
  def of(uriString: String): Try[RelativeUri] =
    Try(new URI(uriString)).flatMap { uri =>
      val path = Option(uri.getPath).getOrElse("")

      Option(uri.getQuery) match {
        case Some(query) =>
          QueryParams.fromString(query).map(FixedRelativeUri(UriPath(path), _, PathParams(Map())))
        case None =>
          scala.util.Success(FixedRelativeUri(UriPath(path), QueryParams.of(), PathParams(Map())))
      }
    }

  def fromPath(path: String): RelativeUri =
    FixedRelativeUri(UriPath(path), QueryParams.of(), PathParams(Map()))
}

sealed trait RelativeUri {
  def query: QueryParams
  def pathParams: PathParams
  def updateQuery(queryParams: QueryParams): RelativeUri
  def updatePathParams(pathParams: PathParams): RelativeUri

  lazy val path: String = {
    this match {
      case FixedRelativeUri(path, _, _) =>
        path.value
      case RewritableRelativeUri(path, _, pathParams) =>
        rewritePathWithPathParams(path.value, pathParams)
    }
  }

  lazy val value: String = {
    val queryString = if (query.toString.nonEmpty) "?" + query.toString else ""
    this match {
      case FixedRelativeUri(path, _, _) => path.value + queryString
      case RewritableRelativeUri(path, _, pathParams) => rewritePathWithPathParams(path.value, pathParams) + queryString
    }
  }

  def modifyQuery(f: QueryParams => QueryParams): RelativeUri =
    updateQuery(f(query))

  def modifyPathParams(f: PathParams => PathParams): RelativeUri =
    updatePathParams(f(pathParams))
}

case class FixedRelativeUri(_path: UriPath, query: QueryParams, pathParams: PathParams) extends RelativeUri {
  def updateQuery(queryParams: QueryParams): FixedRelativeUri = this.copy(query = queryParams)
  def updatePathParams(pathParams: PathParams): FixedRelativeUri = this.copy(pathParams = pathParams)
}

case class RewritableRelativeUri(_path: RewritePath, query: QueryParams, pathParams: PathParams) extends RelativeUri {
  def updateQuery(queryParams: QueryParams): RewritableRelativeUri = this.copy(query = queryParams)
  def updatePathParams(pathParams: PathParams): RewritableRelativeUri = this.copy(pathParams = pathParams)
}

case class QueryParams (private val params: Map[String, List[String]]) {
  def toMap: Map[String, List[String]] = params
  def nonEmpty: Boolean = params.nonEmpty

  override def toString: String =
    params.flatMap { case (name, values) =>
      if (values.nonEmpty)
        values.map(value => s"${encode(name)}=${encode(value)}")
      else List(encode(name))
    }.mkString("&")

  private def encode(s: String): String = URLEncoder.encode(s, "UTF-8")

  def get(name: String): Option[String] =
    params.get(name).flatMap(_.headOption)

  def getValues(name: String): Option[List[String]] =
    params.get(name)

  def set(name: String, value: String): QueryParams =
    this.copy(params = params.updated(name, List(value)))

  def setParams(setPs: Map[String, String]): QueryParams =
    setPs.foldLeft(this) { case (ps, (key, value)) => ps.set(key, value) }

  def setValues(name: String, values: List[String]): QueryParams =
    this.copy(params = params.updated(name, values))

  def remove(name: String): QueryParams =
    this.copy(params = params - name)

  def remove(name: String, value: String): QueryParams =
    params.get(name) match {
      case Some(values) =>
        val filtered = values.filter(value != _)
        if (filtered.nonEmpty) this.setValues(name, filtered) else remove(name)
      case None => this
    }

  def add(name: String, value: String): QueryParams =
    params.get(name) match {
      case Some(oldValues) => this.setValues(name, oldValues ::: List(value))
      case None            => this.set(name, value)
    }

  def addValues(name: String, values: List[String]): QueryParams =
    params.get(name) match {
      case Some(oldValues) => this.setValues(name, oldValues ::: values)
      case None            => this.setValues(name, values)
    }

  def addParams(addPs: Map[String, String]): QueryParams =
    addPs.foldLeft(this) { case (ps, (key, value)) => ps.add(key, value) }

  def addMultiParams(values: Map[String, List[String]]): QueryParams =
    values.foldLeft(this) { case (ps, (key, values)) => ps.addValues(key, values) }

  def contains(name: String): Boolean =
    get(name).nonEmpty

  def contains(name: String, value: String): Boolean =
    getValues(name).exists(_.contains(value))

  def exists(p: ((String, List[String])) => Boolean): Boolean =
    params.exists(p)
}

object QueryParams {
  def empty = new QueryParams(Map())

  def fromString(query: String): Try[QueryParams] =
    Try {
      import scala.collection.JavaConverters._
      /* Given 'y' query exists, but has no value assigned, URLEncodedUtils.parse returns ('y', null).
       * If there are non-null values for the param, we set 'params' to a list of non-null values.
       * If there are no non-null values, but the param exists, we set it to an empty list.
       * Examples:
       *   QueryParams.fromString("y") == QueryParams(Map("y" -> List()))
       *   // Non-null value exists for 'y', so we drop empty 'y', same as how URLEncodedUtils.parse works
       *   QueryParams.fromString("y&y=1") == QueryParams(Map("y" -> List("1")))
       */
      val params: Map[String, List[String]] = URLEncodedUtils.parse(query, defaultCharset()).asScala.toList
        .groupBy(_.getName).mapValues(_.map(_.getValue).filter(_ != null))
      new QueryParams(params)
    }

  def of(ps: Map[String, String]): QueryParams =
    QueryParams(ps.mapValues(List(_)))

  def of(ps: (String, String)*): QueryParams =
    QueryParams(ps.groupBy(_._1).mapValues(_.map(_._2).toList))

  def apply(ps: (String, List[String])*): QueryParams =
    QueryParams(Map(ps:_*))
}

case class UriPath(value: String) extends AnyVal

case class CallOpts(
                     responseTimeout: Option[Int],
                     retries: Option[Int],
                     failureHttpCodes: Option[List[Int]],
                     retryFailedResponse: Option[Boolean],
                     retryOnException: Option[Boolean]
                   )
