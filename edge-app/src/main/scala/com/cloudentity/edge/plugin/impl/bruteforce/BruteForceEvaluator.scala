package com.cloudentity.edge.plugin.impl.bruteforce

import java.time.Instant

import com.cloudentity.tools.vertx.scala.Operation
import com.cloudentity.tools.api.errors.ApiError

trait BruteForceEvaluator {
  val notFoundError = ApiError.`with`(404, "ApiSignature.NotFound", "Api with the given signature does not exist.")

  def isBlocked(attempts: List[BruteForceAttempt]): Boolean = {
    attempts.reverse match {
      case last :: _ => {
        val blockForTimestamp = last.timestamp.plusSeconds(last.blockFor)
        last.blocked && blockForTimestamp.isAfter(Instant.now)
      }
      case Nil => false
    }
  }

  def validateApiSignature[T](element: T, list: List[T]): Operation[ApiError, Unit] = {
    containsOrFail(BruteForcePlugin.cacheCollectionPrefix + element, list, notFoundError)

  }

  def containsOrFail[T](element: T, list: List[T], error: ApiError): Operation[ApiError, Unit] = {
    if (list.contains(element)) Operation.success(())
    else Operation.error(error)
  }
}