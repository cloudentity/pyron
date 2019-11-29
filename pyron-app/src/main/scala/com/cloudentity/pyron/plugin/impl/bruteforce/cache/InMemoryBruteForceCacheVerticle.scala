package com.cloudentity.pyron.plugin.impl.bruteforce.cache

import com.cloudentity.pyron.plugin.impl.bruteforce.{BruteForceAttempt, BruteForceCache}
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.vertx.core.{Future => VxFuture}

import scala.collection.mutable
import scala.concurrent.duration.Duration

class InMemoryBruteForceCacheVerticle extends ScalaServiceVerticle with BruteForceCache {
  case class Cleanup(counter: String, identifier: String, exp: Long)

  var locks: Map[String, Set[String]] = Map()
  var counters: Map[String, Map[String, List[BruteForceAttempt]]] = Map()

  val attemptCleanUps: mutable.PriorityQueue[Cleanup] = new mutable.PriorityQueue[Cleanup]()(Ordering.by(cleanup => -cleanup.exp))

  override def initService(): Unit = {
    vertx.setPeriodic(3000, _ => cleanUpAttempts())
  }

  private def cleanUpAttempts(): Unit = {
    val t = System.currentTimeMillis()
    while (attemptCleanUps.size > 0) {
      val el = attemptCleanUps.dequeue()
      if (el.exp < t) {
        clear(TracingContext.dummy(), el.counter, el.identifier)
      } else {
        attemptCleanUps.enqueue(el)
        return
      }
    }
  }

  override def lock(ctx: TracingContext, counter: String, identifier: String, leaseTime: Duration): VxFuture[Boolean] =
    if (isLocked(counter, identifier)) {
      VxFuture.succeededFuture(false)
    } else {
      locks = locks.updated(counter, locks.getOrElse(counter, Set()) + identifier)
      VxFuture.succeededFuture(true)
    }

  override def unlock(ctx: TracingContext, counter: String, identifier: String): VxFuture[Unit] = {
    val entries = locks.getOrElse(counter, Set())
    locks = locks.updated(counter, entries - identifier)

    VxFuture.succeededFuture(())
  }

  override def set(ctx: TracingContext, counter: String, identifier: String, attempts: List[BruteForceAttempt], ttl: Duration): VxFuture[Unit] = {
    counters.get(counter) match {
      case Some(entries) =>
        counters = counters.updated(counter, entries.updated(identifier, attempts))
      case None =>
        counters = counters.updated(counter, Map(identifier -> attempts))
    }

    attempts.lastOption.foreach(attempt => attemptCleanUps.enqueue(Cleanup(counter, identifier, attempt.timestamp.toEpochMilli + ttl.toMillis)))

    VxFuture.succeededFuture(())
  }

  override def get(ctx: TracingContext, counter: String, identifier: String): VxFuture[Option[List[BruteForceAttempt]]] =
    VxFuture.succeededFuture(counters.getOrElse(counter, Map()).get(identifier))

  private def isLocked(counter: String, identifier: String): Boolean =
    locks.getOrElse(counter, Set()).contains(identifier)

  override def clear(ctx: TracingContext, counter: String, identifier: String): VxFuture[Unit] = {
    counters = counters.updated(counter, counters.getOrElse(counter, Map()) - identifier)

    VxFuture.succeededFuture(())
  }

  override def clearAll(ctx: TracingContext, counter: String): VxFuture[Unit] = {
    counters = counters - counter

    VxFuture.succeededFuture(())
  }

  override def counters(ctx: TracingContext): VxFuture[List[String]] =
    VxFuture.succeededFuture(counters.keys.toList)
}
