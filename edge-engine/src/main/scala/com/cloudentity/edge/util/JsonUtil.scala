package com.cloudentity.edge.util

import io.circe.Json
import io.circe.Json.fromJsonObject

object JsonUtil {
  /**
    * Alternative implementation of io.circe.Json.deepMerge that is computationally cheaper if that.size < ths.size.
    */
  def deepMerge(ths: Json, that: Json): Json = {
    (ths.asObject, that.asObject) match {
      case (Some(lhs), Some(rhs)) =>
        fromJsonObject(
          rhs.toList.foldLeft(lhs) {
            case (acc, (key, value)) =>
              acc(key).fold(acc.add(key, value)) { r => acc.add(key, deepMerge(r, value)) }
          }
        )
      case _ => that
    }
  }
}
