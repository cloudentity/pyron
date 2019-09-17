package com.cloudentity.edge.plugin.verticle

import com.cloudentity.edge.domain.flow.RequestCtx

import scala.concurrent.Future

abstract class ResponsePluginVerticle[C] extends RequestResponsePluginVerticle[C] {
  final def apply(requestCtx: RequestCtx, conf: C): Future[RequestCtx] = Future.failed(new Exception("Tried to apply response plugin to request"))
}