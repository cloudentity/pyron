package com.cloudentity.pyron.domain.flow

import io.circe.Json

object AccessLogItems {
  def apply(cs: (String, Json)*): AccessLogItems = AccessLogItems(cs.toMap)
}

case class AccessLogItems(value: Map[String, Json]) extends AnyVal {
  def apply(v: Map[String, Json]): AccessLogItems = AccessLogItems(v)

  def modify(f: Map[String, Json] => Map[String, Json]): AccessLogItems =
    apply(f(value))

  def get(name: String): Option[Json] =
    value.get(name)

  def updated(name: String, json: Json): AccessLogItems =
    apply(value.updated(name, json))

  def remove(name: String): AccessLogItems =
    apply(value - name)

  def merge(other: AccessLogItems): AccessLogItems =
    apply(value ++ other.value)

  def mergeMap(other: Map[String, Json]): AccessLogItems =
    apply(value ++ other)
}
