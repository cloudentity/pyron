package com.cloudentity.pyron.plugin.impl.bruteforce.cache

import com.cloudentity.pyron.plugin.impl.bruteforce.{Attempt, BruteForceCache}
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.{Future => VxFuture}

import scala.collection.mutable
import scala.concurrent.duration.Duration

class InMemoryBruteForceCacheVerticle extends ScalaServiceVerticle with BruteForceCache {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  case class Cleanup(counter: String, identifier: String, attempt: Attempt, exp: Long)

  var locks: Map[String, Set[String]] = Map()
  var counters: Map[String, Map[String, List[Attempt]]] = Map()

  val attemptCleanUps: mutable.PriorityQueue[Cleanup] = new mutable.PriorityQueue[Cleanup]()(Ordering.by(cleanup => -cleanup.exp))

  override def initService(): Unit = {
    vertx.setPeriodic(3000, _ => cleanUpAttempts())
  }

  private def cleanUpAttempts(): Unit = {
    if (log.isDebugEnabled) {
      log.debug(TracingContext.dummy(), s"Attempts cleanup. Cleanup queue size: ${attemptCleanUps.size}. Counters size: ${counters.values.map(_.values.size).sum}. Locks size: ${locks.values.map(_.size).sum}")
    }

    val t = System.currentTimeMillis()
    while (attemptCleanUps.size > 0) {
      val el = attemptCleanUps.dequeue()
      if (el.exp < t) {
        if (!wasCounterUpdatedAfterCleanupSchedule(el)) {
          clear(TracingContext.dummy(), el.counter, el.identifier)
        }
      } else {
        attemptCleanUps.enqueue(el)
        return
      }
    }
  }

  private def wasCounterUpdatedAfterCleanupSchedule(el: Cleanup): Boolean =
    counters.getOrElse(el.counter, Map()).getOrElse(el.identifier, Nil).headOption.filter(_.timestamp.isAfter(el.attempt.timestamp)).isDefined

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

  override def set(ctx: TracingContext, counter: String, identifier: String, attempts: List[Attempt], ttl: Duration): VxFuture[Unit] = {
    counters.get(counter) match {
      case Some(entries) => counters = counters.updated(counter, entries.updated(identifier, attempts))
      case None          => counters = counters.updated(counter, Map(identifier -> attempts))
    }

    attempts.headOption.foreach(attempt => attemptCleanUps.enqueue(Cleanup(counter, identifier, attempt, attempt.timestamp.toEpochMilli + ttl.toMillis)))

    VxFuture.succeededFuture(())
  }

  override def get(ctx: TracingContext, counter: String, identifier: String): VxFuture[Option[List[Attempt]]] =
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