package com.cloudentity.pyron.plugin

import com.cloudentity.pyron.test.TestRequestResponseCtx
import com.cloudentity.pyron.domain.http._
import com.cloudentity.pyron.domain.flow.{PathParams, ResponseCtx, StaticService, TargetHost}
import com.cloudentity.pyron.domain.http.{ApiResponse, OriginalRequest, QueryParams, RelativeUri, TargetRequest, UriPath}
import com.cloudentity.pyron.plugin.PluginFunctions.ResponsePlugin
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

@RunWith(classOf[JUnitRunner])
class ResponsePluginFunctionsSpec extends WordSpec with MustMatchers with TestRequestResponseCtx {

  val original: OriginalRequest = OriginalRequest(
    method = HttpMethod.GET,
    path = UriPath("uri"),
    queryParams = QueryParams.empty,
    headers = Headers(),
    cookies = Map(),
    bodyOpt = None,
    pathParams = PathParams.empty
  )
  val request: TargetRequest = TargetRequest(
    method = HttpMethod.GET,
    service = StaticService(host = TargetHost("host"), port = 100, ssl = false),
    uri = RelativeUri.of("uri").get,
    headers = Headers(),
    bodyOpt = None
  )

  val response200: ApiResponse = ApiResponse(200, Buffer.buffer(), Headers())
  val response300: ApiResponse = ApiResponse(300, Buffer.buffer(), Headers())
  val response400: ApiResponse = ApiResponse(400, Buffer.buffer(), Headers())

  def responseCtx(resp: ApiResponse): ResponseCtx = emptyResponseCtx.copy(response = resp)

  def failingPlugin(ex: Throwable): ResponsePlugin =
    (_: ResponseCtx) => Future.failed(ex)

  def increaseStatusCodePlugin(increment: Int): ResponsePlugin =
    (ctx: ResponseCtx) => Future.successful(ctx.modifyResponse(_.copy(statusCode = ctx.response.statusCode + increment)))

  "Plugins.applyResponsePlugins" should {
    "return final response when all plugins successful" in {
      //given
      val plugins = List(
        increaseStatusCodePlugin(100),
        increaseStatusCodePlugin(100)
      )

      //when
      val f: Future[ResponseCtx] =
        PluginFunctions.applyResponsePlugins(responseCtx(response200), plugins)(_ => emptyResponse)

      //then
      Await.result(f, 1 second).response mustBe response400
    }

    "not break applying plugins when applied plugin returned failure" in {
      //given
      val ex = new Exception()
      val plugins: List[ResponsePlugin] = List(
        increaseStatusCodePlugin(100),
        failingPlugin(ex),
        increaseStatusCodePlugin(100)
      )

      //when
      val f: Future[ResponseCtx] =
        PluginFunctions.applyResponsePlugins(responseCtx(response200), plugins)(_ => emptyResponse)

      //then
      Await.result(f, 1 second).response mustBe response300
    }
  }
}
