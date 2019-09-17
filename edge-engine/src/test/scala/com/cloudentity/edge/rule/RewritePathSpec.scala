package com.cloudentity.edge.rule

import com.cloudentity.edge.domain.http.PathOperations._
import com.cloudentity.edge.domain.flow.PathParams
import com.cloudentity.edge.domain.http.{OriginalRequest, QueryParams, UriPath}
import com.cloudentity.tools.vertx.http.Headers
import io.vertx.core.http.HttpMethod
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RewritePathSpec extends FlatSpec {

  val originalDefault = OriginalRequest(HttpMethod.GET, UriPath("/path"), QueryParams.empty, Headers(), None, PathParams.empty)

  it should "rewrite path without params" in {
    val rewritePathConf = "/api/new/path"
    val req = originalDefault.copy(pathParams = PathParams.empty)

    assert(rewritePathWithParams(rewritePathConf, req.pathParams) == rewritePathConf)
  }

  it should "rewrite path with one param" in {
    val rewritePathConf = "/api/new/path/{firstParamName}"
    val req = originalDefault.copy(pathParams = PathParams(Map("firstParamName" -> "1-2-3")))

    assert(rewritePathWithParams(rewritePathConf, req.pathParams) == "/api/new/path/1-2-3")
  }

  it should "rewrite path with Two params" in {
    val rewritePathConf = "/api/new/path/{firstParamName}/{secondParamName}"
    val req = originalDefault.copy(pathParams = PathParams(Map("firstParamName" -> "1-2-3", "secondParamName" -> "4-5-6")))

    assert(rewritePathWithParams(rewritePathConf, req.pathParams) == "/api/new/path/1-2-3/4-5-6")
  }

  it should "rewrite path with one param multiple times" in {
    val rewritePathConf = "/api/new/path/{firstParamName}/{firstParamName}"
    val req = originalDefault.copy(pathParams = PathParams(Map("firstParamName" -> "1-2-3")))

    assert(rewritePathWithParams(rewritePathConf, req.pathParams) == "/api/new/path/1-2-3/1-2-3")
  }
}