package com.cloudentity.pyron.domain.http

import org.apache.http.client.utils.URLEncodedUtils

import java.net.URLEncoder
import java.nio.charset.Charset.defaultCharset
import scala.util.Try

case class QueryParams(private val params: Map[String, List[String]]) {
  def toMap: Map[String, List[String]] = params

  def nonEmpty: Boolean = params.nonEmpty

  override def toString: String =
    params.flatMap { case (name, values) =>
      if (values.nonEmpty)
        values.map(value => s"${encode(name)}=${encode(value)}")
      else List(encode(name))
    }.mkString("&")

  private def encode(s: String): String = URLEncoder.encode(s, "UTF-8")

  def setParams(setPs: Map[String, String]): QueryParams =
    setPs.foldLeft(this) { case (ps, (key, value)) => ps.set(key, value) }

  def set(name: String, value: String): QueryParams =
    this.copy(params = params.updated(name, List(value)))

  def remove(name: String, value: String): QueryParams =
    params.get(name) match {
      case Some(values) =>
        val filtered = values.filter(value != _)
        if (filtered.nonEmpty) this.setValues(name, filtered) else remove(name)
      case None => this
    }

  def setValues(name: String, values: List[String]): QueryParams =
    this.copy(params = params.updated(name, values))

  def remove(name: String): QueryParams =
    this.copy(params = params - name)

  def addParams(addPs: Map[String, String]): QueryParams =
    addPs.foldLeft(this) { case (ps, (key, value)) => ps.add(key, value) }

  def add(name: String, value: String): QueryParams =
    params.get(name) match {
      case Some(oldValues) => this.setValues(name, oldValues ::: List(value))
      case None => this.set(name, value)
    }

  def addMultiParams(values: Map[String, List[String]]): QueryParams =
    values.foldLeft(this) { case (ps, (key, values)) => ps.addValues(key, values) }

  def addValues(name: String, values: List[String]): QueryParams =
    params.get(name) match {
      case Some(oldValues) => this.setValues(name, oldValues ::: values)
      case None => this.setValues(name, values)
    }

  def contains(name: String): Boolean =
    get(name).nonEmpty

  def get(name: String): Option[String] =
    params.get(name).flatMap(_.headOption)

  def contains(name: String, value: String): Boolean =
    getValues(name).exists(_.contains(value))

  def getValues(name: String): Option[List[String]] =
    params.get(name)

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
    QueryParams(Map(ps: _*))
}
