package com.cloudentity.edge.util

import com.cloudentity.tools.vertx.scala.Futures
import io.vertx.core.{Future => VxFuture}
import com.cloudentity.tools.vertx.scala.VertxExecutionContext

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

trait FutureUtils {
  def await[A](f: VxFuture[A])(implicit ec: VertxExecutionContext): A =
    Await.result(Futures.toScala(f), 10.seconds)

  def await[A](f: Future[A]): A =
    Await.result(f, 10.seconds)
}
