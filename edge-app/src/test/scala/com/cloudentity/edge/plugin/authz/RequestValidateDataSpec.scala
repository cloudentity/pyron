package com.cloudentity.edge.plugin.authz

import io.circe.Json
import io.circe.parser._
import com.cloudentity.edge.domain._
import com.cloudentity.edge.service.authz.{HeaderValue, RequestValidateData}
import com.cloudentity.edge.test.TestRequestResponseCtx
import com.cloudentity.tools.vertx.http.Headers
import io.vertx.core.buffer.Buffer
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class RequestValidateDataSpec extends WordSpec with MustMatchers with TestRequestResponseCtx {
  "RequestValidateData" should {
    "set cookies" in {
      // given
      val requestCtx = emptyRequestCtx.copy(original = emptyRequestCtx.original.copy(headers = Headers("Cookie" -> List("x=a; y=b", "z=c"))))

      // when
      val result = decode[RequestValidateData](RequestValidateData.toJson(requestCtx).value).getOrElse(???)

      //then
      result.cookies mustBe(Map("x" -> "a", "y" -> "b", "z" -> "c"))
    }

    "set empty body" in {
      // given
      val requestCtx = emptyRequestCtx.copy(original = emptyRequestCtx.original.copy(bodyOpt = Some(Buffer.buffer(""))))

      // when
      val result = decode[RequestValidateData](RequestValidateData.toJson(requestCtx).value).getOrElse(???)

      //then
      result.body mustBe(None)
    }

    "set body if valid JSON" in {
      // given
      val requestCtx = emptyRequestCtx.copy(original = emptyRequestCtx.original.copy(bodyOpt = Some(Buffer.buffer("{}"))))

      // when
      val result = decode[RequestValidateData](RequestValidateData.toJson(requestCtx).value).getOrElse(???)

      //then
      result.body mustBe(Some(Json.obj()))
    }

    "set body even if invalid JSON" in {
      // given
      val requestCtx = emptyRequestCtx.copy(original = emptyRequestCtx.original.copy(bodyOpt = Some(Buffer.buffer("invalid-json"))))

      // when
      val decodeResult = decode[RequestValidateData](RequestValidateData.toJson(requestCtx).value)

      //then
      decodeResult.isLeft mustBe(true)
    }
  }

  "RequestValidateData.HeaderValueEncoder" should {
    val enc = RequestValidateData.HeaderValueEnc
    "encode single header as string" in {
      enc.apply(HeaderValue(Left("x"))) mustBe(Json.fromString("x"))
    }

    "encode multi header as list" in {
      enc.apply(HeaderValue(Right(List("x", "y")))) mustBe(Json.arr(Json.fromString("x"), Json.fromString("y")))
    }
  }
}
