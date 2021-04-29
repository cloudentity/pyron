package com.cloudentity.pyron.domain.http

import com.cloudentity.pyron.domain.flow.{PathParams, RewritePath}
import com.cloudentity.pyron.rule.PreparedPathRewrite.rewritePathWithPathParams

import java.net.URI
import scala.util.Try

case class FixedRelativeUri(_path: UriPath, query: QueryParams, pathParams: PathParams) extends RelativeUri {
  def updateQuery(queryParams: QueryParams): FixedRelativeUri = this.copy(query = queryParams)

  def updatePathParams(pathParams: PathParams): FixedRelativeUri = this.copy(pathParams = pathParams)
}

case class RewritableRelativeUri(_path: RewritePath, query: QueryParams, pathParams: PathParams) extends RelativeUri {
  def updateQuery(queryParams: QueryParams): RewritableRelativeUri = this.copy(query = queryParams)

  def updatePathParams(pathParams: PathParams): RewritableRelativeUri = this.copy(pathParams = pathParams)
}

object RelativeUri {
  def of(uriString: String): Try[RelativeUri] = Try(new URI(uriString)).flatMap { uri =>
    val path = Option(uri.getPath).getOrElse("")
    Option(uri.getQuery).fold(Try(QueryParams.of()))(QueryParams.fromString)
      .map(query => FixedRelativeUri(UriPath(path), query, PathParams(Map())))
  }

  def fromPath(path: String): RelativeUri =
    FixedRelativeUri(UriPath(path), QueryParams.of(), PathParams(Map()))
}

sealed trait RelativeUri {
  lazy val path: String = this match {
    case FixedRelativeUri(path, _, _) => path.value
    case RewritableRelativeUri(path, _, pathParams) => rewritePathWithPathParams(path.value, pathParams)
  }

  lazy val value: String = {
    val queryString = query.toString
    if (queryString.isEmpty) path else path + "?" + queryString
  }

  def query: QueryParams

  def pathParams: PathParams

  def updateQuery(queryParams: QueryParams): RelativeUri

  def updatePathParams(pathParams: PathParams): RelativeUri

  def modifyQuery(f: QueryParams => QueryParams): RelativeUri =
    updateQuery(f(query))

  def modifyPathParams(f: PathParams => PathParams): RelativeUri =
    updatePathParams(f(pathParams))
}
