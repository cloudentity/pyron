package com.cloudentity.pyron.domain.flow

import io.circe.Json

object AuthnCtx {
  val TOKEN_TYPE = "tokenType"

  def apply(cs: (String, Json)*): AuthnCtx = AuthnCtx(cs.toMap)
}

case class AuthnCtx(value: Map[String, Json]) extends AnyVal {
  def apply(v: Map[String, Json]): AuthnCtx = AuthnCtx(v)

  def modify(f: Map[String, Json] => Map[String, Json]): AuthnCtx =
    apply(f(value))

  def get(name: String): Option[Json] =
    value.get(name)

  def updated(name: String, json: Json): AuthnCtx =
    apply(value.updated(name, json))

  def remove(name: String): AuthnCtx =
    apply(value - name)

  def merge(other: AuthnCtx): AuthnCtx =
    apply(value ++ other.value)

  def mergeMap(other: Map[String, Json]): AuthnCtx =
    apply(value ++ other)
}