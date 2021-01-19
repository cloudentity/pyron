package com.cloudentity.pyron.plugin

import com.cloudentity.pyron.test.TestRequestResponseCtx
import com.cloudentity.pyron.domain.flow._
import com.cloudentity.pyron.domain.http._
import com.cloudentity.pyron.plugin.PluginFunctions.RequestPlugin
import com.cloudentity.tools.vertx.http.Headers
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

@RunWith(classOf[JUnitRunner])
class RequestPluginFunctionsSpec extends WordSpec with MustMatchers with TestRequestResponseCtx {
  val host = TargetHost("host")
  val uri = RelativeUri.of("uri").get

  val original = OriginalRequest(HttpMethod.GET, UriPath(uri.path), QueryParams.empty, Headers(), None, PathParams.empty)
  val requestGet = TargetRequest(HttpMethod.GET, StaticService(host, 100, false), uri, Headers(), None)
  val requestPost = TargetRequest(HttpMethod.POST, StaticService(host, 100, false), uri, Headers(), None)
  val requestDelete = TargetRequest(HttpMethod.DELETE, StaticService(host, 100, false), uri, Headers(), None)

  val response = ApiResponse(200, Buffer.buffer(), Headers())

  def requestCtx(req: TargetRequest): RequestCtx =
    emptyRequestCtx.copy(request = req)

  def failingPlugin(ex: Throwable): RequestPlugin =
    (ctx: RequestCtx) => Future.failed(ex)

  def responsePlugin(response: ApiResponse): RequestPlugin =
    (ctx: RequestCtx) => Future.successful(ctx.abort(response))

  def requestPlugin(request: TargetRequest): RequestPlugin =
    (ctx: RequestCtx) => Future.successful(ctx.copy(request = request))

  "Plugins.applyRequestPlugins" should {
    "apply all plugins when all plugins return TargetRequest" in {
      //given
      val plugins = List(requestPlugin(requestPost), requestPlugin(requestDelete))

      //when
      val f: Future[RequestCtx] =
        PluginFunctions.applyRequestPlugins(requestCtx(requestGet), plugins)(_ => emptyResponse)

      //then
      Await.result(f, 1 second).request must be(requestDelete)
    }

    "break applying request plugins when applied plugin returned ApiResponse" in {
      //given
      val plugins = List(requestPlugin(requestPost), responsePlugin(response), requestPlugin(requestDelete))

      //when
      val f: Future[RequestCtx] =
        PluginFunctions.applyRequestPlugins(requestCtx(requestGet), plugins)(_ => emptyResponse)

      //then
      Await.result(f, 1 second).aborted must be(Some(emptyResponse))
    }

    "break applying request plugins when applied plugin returned failure" in {
      //given
      val ex = new Exception()
      val plugins: List[RequestPlugin] = List(requestPlugin(requestPost), failingPlugin(ex), requestPlugin(requestDelete))

      //when
      val f: Future[RequestCtx] =
        PluginFunctions.applyRequestPlugins(requestCtx(requestGet), plugins)(_ => emptyResponse)

      //then
      Await.result(f, 1 second).aborted must be(Some(emptyResponse))
    }
  }


}
