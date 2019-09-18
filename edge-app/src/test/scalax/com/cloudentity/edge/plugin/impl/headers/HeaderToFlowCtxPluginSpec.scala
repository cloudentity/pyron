package com.cloudentity.edge.plugin.impl.headers

import io.circe.Json
import com.cloudentity.edge.domain.flow.{AuthnCtx, RequestCtx}
import com.cloudentity.edge.plugin.impl.headers.{HeaderToFlowCtxConf, HeaderToFlowCtxPlugin}
import com.cloudentity.edge.test.TestRequestResponseCtx
import com.cloudentity.tools.vertx.http.Headers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class HeaderToFlowCtxPluginSpec extends WordSpec with MustMatchers with TestRequestResponseCtx {
  val plugin = new HeaderToFlowCtxPlugin()

  "HeaderToAuthnCtxPlugin" should {
    "not modify request if no headers to copy" in {
      apply(emptyRequestCtx, Nil).request must be(emptyRequest)
    }

    "add single header value to AuthnCtx" in {
      val requestCtx = emptyRequestCtx.modifyRequest(_.copy(headers = Headers("header1" -> List("value1"))))
      val headers = List("header1")

      apply(requestCtx, headers).authnCtx must be(AuthnCtx("header1" -> Json.fromString("value1")))
    }

    "add multi header values to AuthnCtx" in {
      val requestCtx = emptyRequestCtx.modifyRequest(_.copy(headers = Headers("header1" -> List("value1", "value2"))))
      val headers = List("header1")

      apply(requestCtx, headers).authnCtx must be(AuthnCtx("header1" -> Json.arr(Json.fromString("value1"), Json.fromString("value2"))))
    }
  }

  private def apply(ctx: RequestCtx, headers: List[String]): RequestCtx =
    Await.result(plugin.apply(ctx, HeaderToFlowCtxConf(headers)), 1 second)
}
