package com.cloudentity.pyron.domain.flow

import scala.util.Try

object Properties {
  def apply(ps: (String, Any)*): Properties = Properties(Map(ps: _*))
}

case class Properties(private val ps: Map[String, Any]) {
  def toMap(): Map[String, Any] = ps

  def updated(key: String, value: Any): Properties =
    Properties(ps.updated(key, value))

  def get[A](key: String): Option[A] =
    ps.get(key).flatMap { value => Try(value.asInstanceOf[A]).toOption }
}