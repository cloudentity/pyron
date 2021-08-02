package com.cloudentity.pyron.plugin.impl.transform

import com.cloudentity.pyron.domain.flow.ResponseCtx
import com.cloudentity.pyron.domain.http.ApiResponse
import com.cloudentity.pyron.plugin.impl.transform.TransformHttpStatus.transformHttpStatus
import com.cloudentity.pyron.plugin.util.value._
import com.cloudentity.pyron.test.TestRequestResponseCtx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.{JsonArray, JsonObject}
import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TransformHttpStatusTest extends WordSpec with MustMatchers with TestRequestResponseCtx {

  "TransformHttpStatus.transformHttpStatus" should {
    def responseCtx = emptyResponseCtx

    "set new value of httpStatus" in {
      transformHttpStatus(Some(201))(responseCtx) mustBe expectedResponseStatus(responseCtx, 201)
    }

    "do not change httpStatus" in {
      transformHttpStatus(None)(responseCtx) mustBe responseCtx
    }
  }

  def expectedResponseStatus(responseCtx: ResponseCtx, status: Int): ResponseCtx = {
    val response: ApiResponse = responseCtx.response.copy(statusCode = status)
    responseCtx.modifyResponse(r => response)
  }
}
