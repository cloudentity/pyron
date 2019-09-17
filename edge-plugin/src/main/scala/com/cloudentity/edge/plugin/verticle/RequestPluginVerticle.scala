package com.cloudentity.edge.plugin.verticle

import com.cloudentity.edge.domain._
import com.cloudentity.edge.domain.flow.ResponseCtx

import scala.concurrent.Future

/**
  * @tparam C plugin configuration (e.g. case class AuthzPluginConf(policy: String))
  */
abstract class RequestPluginVerticle[C] extends RequestResponsePluginVerticle[C] {
  final def apply(responseCtx: ResponseCtx, conf: C): Future[ResponseCtx] = Future.successful(responseCtx)
}