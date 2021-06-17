package com.cloudentity.pyron.plugin.impl.transform

import com.cloudentity.pyron.domain.flow.PathParams
import com.cloudentity.pyron.domain.http.QueryParams
import com.cloudentity.pyron.plugin.impl.transform.TransformHeaders.setHeaders
import com.cloudentity.pyron.plugin.impl.transform.TransformJsonBody.{applyBodyTransformations, setJsonBody}
import com.cloudentity.pyron.plugin.impl.transform.TransformPathParams.setPathParams
import com.cloudentity.pyron.plugin.impl.transform.TransformQueryParams.setQueryParams
import com.cloudentity.pyron.plugin.util.value._
import com.cloudentity.pyron.test.TestRequestResponseCtx
import com.cloudentity.tools.vertx.http.Headers
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.{JsonArray, JsonObject}
import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TransformHeadersTest extends WordSpec with MustMatchers with TestRequestResponseCtx {

  "TransformHeaders.set" should {

    "set value for non-existing header" in {
      setHeaders(Map("a" -> Option(List("x"))))(Headers()) mustBe Headers.of("a" -> "x")
    }

    "set multi-value for non-existing header" in {
      setHeaders(Map("a" -> Option(List("x", "y"))))(Headers()) mustBe Headers.of("a" -> "x", "a" -> "y")
    }

    "set value for existing header" in {
      setHeaders(Map("a" -> Option(List("x"))))(Headers.of("a" -> "y")) mustBe Headers.of("a" -> "x")
    }

    "set none value for non-existing header" in {
      setHeaders(Map("a" -> None))(Headers()) mustBe Headers()
    }

    "set none value for existing header" in {
      setHeaders(Map("a" -> None))(Headers.of("a" -> "x")) mustBe Headers()
    }
  }
}
