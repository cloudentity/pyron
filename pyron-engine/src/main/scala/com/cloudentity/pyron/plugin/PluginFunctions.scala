package com.cloudentity.pyron.plugin

import com.cloudentity.pyron.domain.flow.{RequestCtx, RequestPluginFailure, ResponseCtx, ResponsePluginFailure}
import com.cloudentity.pyron.domain.http.ApiResponse

import scala.concurrent.{ExecutionContext, Future}

object PluginFunctions {
  private type Plugin[Ctx] = Ctx => Future[Ctx]
  type RequestPlugin  = Plugin[RequestCtx] // NOTE: RequestPlugin may return failed Future
  type ResponsePlugin = Plugin[ResponseCtx] // NOTE: ResponsePlugin may return failed Future

  def applyRequestPlugins(requestCtx: RequestCtx, plugins: List[RequestPlugin])
                         (recoveredResponse: Throwable => ApiResponse)
                         (implicit ec: ExecutionContext): Future[RequestCtx] =
    applyPlugins(requestCtx, plugins)((ctx, plugin) => ctx.aborted.fold {
      plugin(ctx).recover { case ex =>
        ctx.copy(failed = Some(RequestPluginFailure)).abort(recoveredResponse(ex))
      }
    } { _ => Future.successful(ctx) })

  def applyResponsePlugins(responseCtx: ResponseCtx, plugins: List[ResponsePlugin])
                          (recoveredResponse: Throwable => ApiResponse)
                          (implicit ec: ExecutionContext): Future[ResponseCtx] =
    applyPlugins(responseCtx, plugins)((ctx, plugin) =>
      plugin(ctx).recover { case ex =>
        ctx.copy(failed = Some(ResponsePluginFailure), response = recoveredResponse(ex))
      })

  private def applyPlugins[Ctx](ctx: Ctx, plugins: List[Plugin[Ctx]])
                               (applyPlugin: (Ctx, Plugin[Ctx]) => Future[Ctx])
                               (implicit ec: ExecutionContext): Future[Ctx] = {
    val initial = Future.successful(ctx)
    plugins.foldLeft(initial) { (ctxFuture, plugin) => ctxFuture.flatMap(applyPlugin(_, plugin)) }
  }

}
