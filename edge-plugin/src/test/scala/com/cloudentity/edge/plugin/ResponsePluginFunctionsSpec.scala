package com.cloudentity.edge.plugin

import com.cloudentity.edge.test.TestRequestResponseCtx
import com.cloudentity.edge.domain.http._
import com.cloudentity.edge.domain.flow.{PathParams, ResponseCtx, StaticService, TargetHost}
import com.cloudentity.edge.domain.http.{ApiResponse, OriginalRequest, QueryParams, RelativeUri, TargetRequest, UriPath}
import com.cloudentity.edge.plugin.PluginFunctions.ResponsePlugin
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

@RunWith(classOf[JUnitRunner])
class ResponsePluginFunctionsSpec extends WordSpec with MustMatchers with TestRequestResponseCtx {
  val original = OriginalRequest(HttpMethod.GET, UriPath("uri"), QueryParams.empty, Headers(), None, PathParams.empty)
  val request = TargetRequest(HttpMethod.GET, StaticService(TargetHost("host"), 100, false), RelativeUri.of("uri").get, Headers(), None)

  val response200 = ApiResponse(200, Buffer.buffer(), Headers())
  val response400 = ApiResponse(400, Buffer.buffer(), Headers())

  def responseCtx(resp: ApiResponse) = emptyResponseCtx.copy(response = resp)

  def failingPlugin(ex: Throwable): ResponsePlugin =
    (ctx: ResponseCtx) => Future.failed(ex)

  def increaseStatusCodePlugin(increment: Int): ResponsePlugin =
    (ctx: ResponseCtx) => Future.successful(ctx.modifyResponse(_.copy(statusCode = ctx.response.statusCode + increment)))

  "Plugins.applyResponsePlugins" should {
    "return final response when all plugins successful" in {
      //given
      val plugins = List(increaseStatusCodePlugin(100), increaseStatusCodePlugin(100))

      //when
      val f: Future[ResponseCtx] =
        PluginFunctions.applyResponsePlugins(responseCtx(response200), plugins)

      //then
      Await.result(f, 1 second).response must be(response400)
    }

    "break applying plugins when applied plugin returned failure" in {
      //given
      val ex = new Exception()
      val plugins: List[ResponsePlugin] = List(increaseStatusCodePlugin(100), failingPlugin(ex), increaseStatusCodePlugin(100))

      //when
      val f: Future[ResponseCtx] =
        PluginFunctions.applyResponsePlugins(responseCtx(response200), plugins)

      //then
      Await.result(f.failed, 1 second) must be(ex)
    }
  }
}
