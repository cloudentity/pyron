package com.cloudentity.pyron.plugin

import com.cloudentity.pyron.domain.flow.{RequestCtx, ResponseCtx}

import scala.concurrent.{ExecutionContext, Future}

object PluginFunctions {
  type RequestPlugin  = RequestCtx => Future[RequestCtx] // NOTE: RequestPlugin may return failed Future
  type ResponsePlugin = ResponseCtx => Future[ResponseCtx] // NOTE: ResponsePlugin may return failed Future

  /**
    * Applies request plugins.
    */
  def applyRequestPlugins(requestCtx: RequestCtx, plugins: List[RequestPlugin])(implicit ec: ExecutionContext): Future[RequestCtx] = {
    val initial = Future.successful[RequestCtx](requestCtx)
    plugins.foldLeft(initial) { (future, plugin) =>
      future.flatMap { ctx =>
        ctx.aborted match {
          case Some(_) => Future.successful(ctx)
          case None    => plugin.apply(ctx)
        }
      }
    }
  }

  def applyResponsePlugins(responseCtx: ResponseCtx, plugins: List[ResponsePlugin])(implicit ec: ExecutionContext): Future[ResponseCtx] = {
    val initial = Future.successful(responseCtx)
    plugins.foldLeft(initial) { (fr, p) =>
      fr.flatMap(resp => p.apply(resp))
    }
  }
}