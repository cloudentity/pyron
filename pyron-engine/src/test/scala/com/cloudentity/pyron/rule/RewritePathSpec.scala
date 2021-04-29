package com.cloudentity.pyron.rule

import com.cloudentity.pyron.domain.flow.PathParams
import com.cloudentity.pyron.domain.http.{OriginalRequest, QueryParams, UriPath}
import com.cloudentity.pyron.rule.PreparedPathRewrite.rewritePathWithPathParams
import com.cloudentity.tools.vertx.http.Headers
import io.vertx.core.http.HttpMethod
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RewritePathSpec extends FlatSpec {

  val originalDefault: OriginalRequest = OriginalRequest(
    method = HttpMethod.GET,
    path = UriPath("/path"),
    scheme = "http",
    host = "host",
    localHost = "localHost",
    remoteHost = "remoteHost",
    pathParams = PathParams.empty,
    queryParams = QueryParams.empty,
    headers = Headers(),
    cookies = Map(),
    bodyOpt = None
  )

  it should "rewrite path without params" in {
    val rewritePathConf = "/api/new/path"
    val req = originalDefault.copy(pathParams = PathParams.empty)

    assert(rewritePathWithPathParams(rewritePathConf, req.pathParams) == rewritePathConf)
  }

  it should "rewrite path with one param" in {
    val rewritePathConf = "/api/new/path/{paramOne}"
    val req = originalDefault.copy(pathParams = PathParams(Map("paramOne" -> "1-2-3")))

    assert(rewritePathWithPathParams(rewritePathConf, req.pathParams) == "/api/new/path/1-2-3")
  }

  it should "rewrite path with two params" in {
    val rewritePathConf = "/api/new/path/{paramOne}/{paramTwo}"
    val req = originalDefault.copy(pathParams = PathParams(Map("paramOne" -> "1-2-3", "paramTwo" -> "4-5-6")))

    assert(rewritePathWithPathParams(rewritePathConf, req.pathParams) == "/api/new/path/1-2-3/4-5-6")
  }

  it should "rewrite path with repeated params" in {
    val rewritePathConf = "/api/{paramTwo}/new/path/{paramOne}/{paramOne}-{paramTwo}"
    val req = originalDefault.copy(pathParams = PathParams(Map("paramOne" -> "1-2-3", "paramTwo" -> "4-5-6")))

    assert(rewritePathWithPathParams(rewritePathConf, req.pathParams) == "/api/4-5-6/new/path/1-2-3/1-2-3-4-5-6")
  }
}