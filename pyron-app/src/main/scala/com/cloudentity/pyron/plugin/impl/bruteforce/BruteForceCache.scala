package com.cloudentity.pyron.plugin.impl.bruteforce

import com.cloudentity.tools.vertx.bus.VertxEndpoint
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.vertx.core.{Future => VxFuture}

import scala.concurrent.duration.Duration

trait BruteForceCache {
  @VertxEndpoint def lock(ctx: TracingContext, counter: String, identifier: String, leaseTime: Duration): VxFuture[Boolean]
  @VertxEndpoint def unlock(ctx: TracingContext, counter: String, identifier: String): VxFuture[Unit]
  @VertxEndpoint def set(ctx: TracingContext, counter: String, identifier: String, attempts: List[BruteForceAttempt], ttl: Duration): VxFuture[Unit]
  @VertxEndpoint def get(ctx: TracingContext, counter: String, identifier: String): VxFuture[Option[List[BruteForceAttempt]]]
  @VertxEndpoint def clear(ctx: TracingContext, counter: String, identifier: String): VxFuture[Unit]
  @VertxEndpoint def clearAll(ctx: TracingContext, counter: String): VxFuture[Unit]
  @VertxEndpoint def counters(ctx: TracingContext): VxFuture[List[String]]
}