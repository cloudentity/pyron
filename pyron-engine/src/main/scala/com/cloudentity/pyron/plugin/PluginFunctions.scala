package com.cloudentity.pyron.plugin

import com.cloudentity.pyron.domain.flow.{RequestCtx, RequestPluginFailure, ResponseCtx, ResponsePluginFailure}
import com.cloudentity.pyron.domain.http.ApiResponse

import scala.concurrent.{ExecutionContext, Future}

object PluginFunctions {
  type RequestPlugin  = RequestCtx => Future[RequestCtx] // NOTE: RequestPlugin may return failed Future
  type ResponsePlugin = ResponseCtx => Future[ResponseCtx] // NOTE: ResponsePlugin may return failed Future

  /**
    * Applies request plugins.
    */
  def applyRequestPlugins(requestCtx: RequestCtx, plugins: List[RequestPlugin])(recoveredResponse: Throwable => ApiResponse)(implicit ec: ExecutionContext): Future[RequestCtx] = {
    val initial = Future.successful(requestCtx)
    plugins.foldLeft(initial) { (future, plugin) =>
      future.flatMap { ctx =>
        ctx.aborted match {
          case Some(_) => Future.successful(ctx)
          case None    => plugin.apply(ctx).recover { case ex => ctx.copy(failed = Some(RequestPluginFailure)).abort(recoveredResponse(ex)) }
        }
      }
    }
  }

  def applyResponsePlugins(responseCtx: ResponseCtx, plugins: List[ResponsePlugin])(recoveredResponse: Throwable => ApiResponse)(implicit ec: ExecutionContext): Future[ResponseCtx] = {
    val initial = Future.successful(responseCtx)
    plugins.foldLeft(initial) { (future, plugin) =>
      future.flatMap { ctx =>
        plugin.apply(ctx).recover { case ex => ctx.copy(response = recoveredResponse(ex), failed = Some(ResponsePluginFailure)) }
      }
    }
  }
}