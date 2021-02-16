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
  val host: TargetHost = TargetHost("host")
  val uri: RelativeUri = RelativeUri.of("uri").get

  val original: OriginalRequest = OriginalRequest(
    HttpMethod.GET,
    UriPath(uri.path),
    QueryParams.empty,
    Headers(),
    None,
    PathParams.empty
  )
  val requestGet: TargetRequest = TargetRequest(
    HttpMethod.GET,
    StaticService(host = host, port = 100, ssl = false),
    uri,
    Headers(),
    None
  )
  val requestPost: TargetRequest = TargetRequest(
    method = HttpMethod.POST,
    service = StaticService(host = host, port = 100, ssl = false),
    uri = uri,
    headers = Headers(),
    bodyOpt = None
  )
  val requestDelete: TargetRequest = TargetRequest(
    method = HttpMethod.DELETE,
    service = StaticService(host = host, port = 100, ssl = false),
    uri = uri,
    headers = Headers(),
    bodyOpt = None
  )

  val response: ApiResponse = ApiResponse(200, Buffer.buffer(), Headers())

  def requestCtx(req: TargetRequest): RequestCtx =
    emptyRequestCtx.copy(request = req)

  def failingPlugin(ex: Throwable): RequestPlugin =
    (_: RequestCtx) => Future.failed(ex)

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
      Await.result(f, 1 second).request mustBe requestDelete
    }

    "break applying request plugins when applied plugin returned ApiResponse" in {
      //given
      val plugins = List(requestPlugin(requestPost), responsePlugin(response), requestPlugin(requestDelete))

      //when
      val f: Future[RequestCtx] =
        PluginFunctions.applyRequestPlugins(requestCtx(requestGet), plugins)(_ => emptyResponse)

      //then
      Await.result(f, 1 second).aborted mustBe Some(emptyResponse)
    }

    "break applying request plugins when applied plugin returned failure" in {
      //given
      val ex = new Exception()
      val plugins: List[RequestPlugin] = List(requestPlugin(requestPost), failingPlugin(ex), requestPlugin(requestDelete))

      //when
      val f: Future[RequestCtx] =
        PluginFunctions.applyRequestPlugins(requestCtx(requestGet), plugins)(_ => emptyResponse)

      //then
      Await.result(f, 1 second).aborted mustBe Some(emptyResponse)
    }
  }


}
