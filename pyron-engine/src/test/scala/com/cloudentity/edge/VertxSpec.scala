package com.cloudentity.pyron

import com.cloudentity.tools.vertx.bus.VertxBus
import io.vertx.core.{Verticle, Vertx}
import com.cloudentity.tools.vertx.scala.{FutureConversions, VertxExecutionContext}
import com.cloudentity.tools.vertx.verticles.VertxDeploy

import scala.concurrent.Await
import scala.concurrent.duration._

trait VertxSpec extends FutureConversions {
  lazy val vertx: Vertx = Vertx.vertx()
  implicit lazy val ec = VertxExecutionContext(this.vertx.getOrCreateContext())

  VertxBus.registerPayloadCodec(vertx.eventBus())

  def deployVerticle(plugin: Verticle): Unit = {
    Await.result(VertxDeploy.deploy(vertx, plugin).toScala, 3 seconds)
  }
}
