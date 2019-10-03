package com.cloudentity.pyron.plugin.verticle

import com.cloudentity.pyron.domain.flow.RequestCtx

import scala.concurrent.Future

abstract class ResponsePluginVerticle[C] extends RequestResponsePluginVerticle[C] {
  final def apply(requestCtx: RequestCtx, conf: C): Future[RequestCtx] = Future.failed(new Exception("Tried to apply response plugin to request"))
}