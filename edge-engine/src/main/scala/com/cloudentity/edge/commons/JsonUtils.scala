package com.cloudentity.edge.commons

import io.circe.Json

import scala.annotation.tailrec

object JsonUtils {

  /**
    * Finds json value at given period-separated path.
    *
    * E.g.
    * json   = { "a": { "b": "c" } }
    * path   = "a.b.c"
    * result = "c"
    */
  def find(json: Json, path: String): Option[Json] =
    find(json, path.split('.').toList)

/**
  * Finds json value at given path.
  */
  @tailrec def find(json: Json, path: List[String]): Option[Json] = {
    path match {
      case h :: Nil =>
        json.asObject.flatMap(_.apply(h))
      case h :: tail =>
        json.asObject.flatMap(_.apply(h)) match {
          case Some(subJson) => find(subJson, tail)
          case None => None
        }
      case Nil => None
    }
  }
}
