package com.cloudentity.pyron.acceptance

import com.cloudentity.pyron.PyronAcceptanceTest
import com.cloudentity.pyron.api.ProxyHeadersHandler
import com.cloudentity.pyron.api.ProxyHeadersHandler._
import com.cloudentity.pyron.config.Conf.ProxyHeaderConf
import com.cloudentity.pyron.domain.Codecs._
import com.cloudentity.pyron.domain.flow.{ProxyHeaders, RequestCtx}
import io.circe.parser._
import io.restassured.RestAssured.given
import io.vertx.core.MultiMap
import org.hamcrest.{BaseMatcher, Description}
import org.junit.Test
import org.scalatest.MustMatchers

class ProxyHeadersHandlerAcceptanceTest  extends PyronAcceptanceTest with MustMatchers {
  override def getMetaConfPath(): String ="src/test/resources/acceptance/proxy-headers/meta-config.json"

  def headerMatcher(name: String, value: String) = new BaseMatcher[String] {
    override def matches(o: Any): Boolean =
      decode[RequestCtx](o.toString).toTry.get.request.headers.get(name).contains(value)

    override def describeTo(description: Description): Unit =
      description.appendText(s"Expected header name=$name, value=$value")
  }

  val inTrueClientIpHeader = "IN-MY-TRUE-IP"
  val outTrueClientIpHeader = "OUT-MY-TRUE-IP"

    def shouldReadTrueClientIpFromCustomHeaderAndWriteToCustomHeaderWithFullStackRunning(): Unit = {
      val ip = "80.80.80.80"

      given()
        .header(inTrueClientIpHeader, ip)
      .when()
        .get("/proxy-headers")
      .`then`()
        .body(headerMatcher(outTrueClientIpHeader, ip))
    }
  val remoteHost = "host"
  val remoteIp = "80.80.80.80"

  @Test
  def shouldReadTrueClientIpFromDefaultHeaderAndWriteToDefaultHeader(): Unit = {
    // given
    val headerNames = ProxyHeaderConf(None, None, None)
    val trueIp = "10.10.10.10"
    val headers = MultiMap.caseInsensitiveMultiMap.add(ProxyHeadersHandler.defaultTrueClientIpHeader, trueIp)

    // when then
    val expectedHeaders = Map(
      xForwardedForHeader       -> List(remoteIp),
      xForwardedHostHeader      -> List(remoteHost),
      xForwardedProtoHeader     -> List("http"),
      defaultTrueClientIpHeader -> List(trueIp)
    )
    val expectedTrueClientIp = trueIp

    ProxyHeadersHandler.proxyHeaders(headers, remoteIp, Option(remoteHost), false, headerNames) must be(ProxyHeaders(expectedHeaders, expectedTrueClientIp))
  }

  @Test
  def shouldReadTrueClientIpFromDefaultHeaderAndWriteToCustomHeader(): Unit = {
      // given
      val headerNames = ProxyHeaderConf(None, Some(outTrueClientIpHeader), None)
      val trueIp = "10.10.10.10"
      val headers = MultiMap.caseInsensitiveMultiMap.add(ProxyHeadersHandler.defaultTrueClientIpHeader, trueIp)

      // when then
      val expectedHeaders = Map(
        xForwardedForHeader   -> List(remoteIp),
        xForwardedHostHeader  -> List(remoteHost),
        xForwardedProtoHeader -> List("http"),
        outTrueClientIpHeader -> List(trueIp)
      )
      val expectedTrueClientIp = trueIp

      ProxyHeadersHandler.proxyHeaders(headers, remoteIp, Option(remoteHost), false, headerNames) must be(ProxyHeaders(expectedHeaders, expectedTrueClientIp))
    }

  @Test
  def shouldReadTrueClientIpFromCustomHeaderAndWriteToDefaultHeader(): Unit = {
    // given
    val headerNames = ProxyHeaderConf(Some(inTrueClientIpHeader), None, None)
    val trueIp = "10.10.10.10"
    val headers = MultiMap.caseInsensitiveMultiMap.add(inTrueClientIpHeader, trueIp)

    // when then
    val expectedHeaders = Map(
      xForwardedForHeader       -> List(remoteIp),
      xForwardedHostHeader      -> List(remoteHost),
      xForwardedProtoHeader     -> List("http"),
      defaultTrueClientIpHeader -> List(trueIp)
    )
    val expectedTrueClientIp = trueIp

    ProxyHeadersHandler.proxyHeaders(headers, remoteIp, Option(remoteHost), false, headerNames) must be(ProxyHeaders(expectedHeaders, expectedTrueClientIp))
  }

  @Test
  def shouldReadTrueClientIpFromCustomHeaderAndWriteToCustomHeader(): Unit = {
    // given
    val headerNames = ProxyHeaderConf(Some(inTrueClientIpHeader), Some(outTrueClientIpHeader), None)
    val trueIp = "10.10.10.10"
    val headers = MultiMap.caseInsensitiveMultiMap.add(inTrueClientIpHeader, trueIp)

    // when then
    val expectedHeaders = Map(
      xForwardedForHeader       -> List(remoteIp),
      xForwardedHostHeader      -> List(remoteHost),
      xForwardedProtoHeader     -> List("http"),
      outTrueClientIpHeader     -> List(trueIp)
    )
    val expectedTrueClientIp = trueIp

    ProxyHeadersHandler.proxyHeaders(headers, remoteIp, Option(remoteHost), false, headerNames) must be(ProxyHeaders(expectedHeaders, expectedTrueClientIp))
  }

  @Test
  def shouldSetTrueClientIpFromRemoteAddressIfMissingTrueClientIpHeaderAndXForwardedForEmpty(): Unit = {
    // given
    val headerNames = ProxyHeaderConf(None, None, None)
    val headers = MultiMap.caseInsensitiveMultiMap

    // when then
    val expectedHeaders = Map(
      xForwardedForHeader       -> List(remoteIp),
      xForwardedHostHeader      -> List(remoteHost),
      xForwardedProtoHeader     -> List("http"),
      defaultTrueClientIpHeader -> List(remoteIp)
    )
    val expectedTrueClientIp = remoteIp

    ProxyHeadersHandler.proxyHeaders(headers, remoteIp, Option(remoteHost), false, headerNames) must be(ProxyHeaders(expectedHeaders, expectedTrueClientIp))
  }

  @Test
  def shouldSetTrueClientIpToFirstXForwardedForIfMissingTrueClientIpHeaderAndXForwardedForNonEmpty(): Unit = {
    // given
    val headerNames = ProxyHeaderConf(None, None, None)
    val trueIp = "10.10.10.10"
    val headers = MultiMap.caseInsensitiveMultiMap.add(xForwardedForHeader, trueIp).add(xForwardedHostHeader, "domain.com")

    // when then
    val expectedHeaders = Map(
      xForwardedForHeader       -> List(trueIp, remoteIp),
      xForwardedHostHeader      -> List("domain.com", remoteHost),
      xForwardedProtoHeader     -> List("http"),
      defaultTrueClientIpHeader -> List(trueIp)
    )
    val expectedTrueClientIp = trueIp

    ProxyHeadersHandler.proxyHeaders(headers, remoteIp, Option(remoteHost), false, headerNames) must be(ProxyHeaders(expectedHeaders, expectedTrueClientIp))
  }
}
