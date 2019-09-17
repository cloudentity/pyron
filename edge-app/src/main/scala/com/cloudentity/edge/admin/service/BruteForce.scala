package com.cloudentity.edge.admin.service

import io.vertx.core.{Future => VxFuture}
import com.cloudentity.edge.plugin.impl.bruteforce.BruteForceAttempt
import com.cloudentity.edge.plugin.impl.bruteforce.BruteForcePlugin.cacheCollectionPrefix
import com.cloudentity.tools.vertx.hazelcast.HazelcastServiceClient
import com.cloudentity.tools.vertx.scala.FutureConversions

object BruteForce extends FutureConversions {

  def getBruteForceAttempts(cache: HazelcastServiceClient)(counterName: String, identifier: String): VxFuture[Option[List[BruteForceAttempt]]] =
    cache.getValue[List[BruteForceAttempt]](cacheCollectionPrefix + counterName, identifier)

  def clearUserBruteForceAttempts(cache: HazelcastServiceClient)(counterName: String, identifier: String): VxFuture[Unit] =
    cache.removeValue(cacheCollectionPrefix + counterName, identifier)

  def clearBruteForceAttempts(cache: HazelcastServiceClient)(counterName: String): VxFuture[Unit] = {
    cache.clearMap(cacheCollectionPrefix + counterName)
  }

  def getBruteForceListNames(cache: HazelcastServiceClient): VxFuture[List[String]] = {
    cache.getCollectionNames().compose { collections =>
      VxFuture.succeededFuture(collections.filter(_.startsWith(cacheCollectionPrefix)).filterNot(_.endsWith(".lock")))
    }
  }

  val idPattern = """(.+)::(.+)""".r

  /**
    * `id` can be regular string or a string value prefixed with `{id-type}::`.
    *  If it's the latter case then we need to strip prefix.
    */
  def extractIdentifierValue(id: String): String =
    idPattern.findFirstMatchIn(id) match {
      case Some(m) => m.group(2)
      case None    => id
    }
}
