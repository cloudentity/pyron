package com.cloudentity.edge.plugin.impl.authn.methods.oauth10

import java.net.URLEncoder

import com.cloudentity.edge.domain._
import com.cloudentity.edge.domain.flow.PathParams
import com.cloudentity.edge.domain.http.{FixedRelativeUri, OriginalRequest, QueryParams, UriPath}
import com.cloudentity.tools.vertx.http.Headers
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import org.junit.{Ignore, Test}
import org.scalatest.MustMatchers
import scalaz.{-\/, \/, \/-}

import scala.util.Success

class OAuth10SignatureBaseStringBuilderTest extends MustMatchers {

  val colonDoubleSlash = "%3A%2F%2F"
  val slash = "%2F"
  val equals = "%3D"
  val amp = "%26"
  val colon = "%3A"

  val sampleParams = OAuth10Request("key", None, "signature", "method", 123, "nonce", None)
  val secureDomain = PublicDomainConf("example.com", 443, true)
  val sampleXmlbody = """<?xml version="1.0" encoding="Windows-1252"?><ns2:TerminationInquiryRequest xmlns:ns2="http://mastercard.com/termination"><AcquirerId>1996</AcquirerId><TransactionReferenceNumber>1</TransactionReferenceNumber><Merchant><Name>TEST</Name><DoingBusinessAsName>TEST</DoingBusinessAsName><PhoneNumber>5555555555</PhoneNumber><NationalTaxId>1234567890</NationalTaxId><Address><Line1>5555 Test Lane</Line1><City>TEST</City><CountrySubdivision>XX</CountrySubdivision><PostalCode>12345</PostalCode><Country>USA</Country></Address><Principal><FirstName>John</FirstName><LastName>Smith</LastName><NationalId>1234567890</NationalId><PhoneNumber>5555555555</PhoneNumber><Address><Line1>5555 Test Lane</Line1><City>TEST</City><CountrySubdivision>XX</CountrySubdivision><PostalCode>12345</PostalCode><Country>USA</Country></Address><DriversLicense><Number>1234567890</Number><CountrySubdivision>XX</CountrySubdivision></DriversLicense></Principal></Merchant></ns2:TerminationInquiryRequest>"""
  val sampleXmlBodyHash = "h2Pd7zlzEZjZVIKB4j94UZn/xxoR3RoCjYQ9/JdadGQ="

  @Test
  def simpleSecureGet(): Unit = {
    val request = buildOriginalRequest(HttpMethod.GET, "/test", None, PathParams.empty, QueryParams())
    val res = OAuth10SignatureBaseStringBuilder.build(TracingContext.dummy(), request, sampleParams, secureDomain)

    val expectedBaseUrl = s"GET&https${colonDoubleSlash}example.com${slash}test&"
    val expectedQueryParams = s"oauth_consumer_key${equals}${sampleParams.consumerKey}${amp}" +
      s"oauth_nonce${equals}${sampleParams.nonce}${amp}" +
      s"oauth_signature_method${equals}${sampleParams.signatureMethod}${amp}" +
      s"oauth_timestamp${equals}${sampleParams.timestamp.toString}"

    assertSignatureEquals(res, expectedBaseUrl, expectedQueryParams)
  }

  @Test
  def getWithCustomPort(): Unit = {
    val request = buildOriginalRequest(HttpMethod.GET, "/test", None, PathParams.empty, QueryParams())
    val conf = PublicDomainConf("example.com", 8080, false)
    val res = OAuth10SignatureBaseStringBuilder.build(TracingContext.dummy(), request, sampleParams, conf)

    val expectedBaseUrl = s"GET&http${colonDoubleSlash}example.com${colon}8080${slash}test&"
    val expectedQueryParams = s"oauth_consumer_key${equals}${sampleParams.consumerKey}${amp}" +
      s"oauth_nonce${equals}${sampleParams.nonce}${amp}" +
      s"oauth_signature_method${equals}${sampleParams.signatureMethod}${amp}" +
      s"oauth_timestamp${equals}${sampleParams.timestamp.toString}"

    assertSignatureEquals(res, expectedBaseUrl, expectedQueryParams)
  }

  @Test
  def getWithQueryParam(): Unit = {
    val request = buildOriginalRequest(HttpMethod.GET, "/test", None, PathParams.empty, QueryParams("key" -> List("value")))
    val res = OAuth10SignatureBaseStringBuilder.build(TracingContext.dummy(), request, sampleParams, secureDomain)

    val expectedBaseUrl = s"GET&https${colonDoubleSlash}example.com${slash}test&"
    val expectedQueryParams = s"key${equals}value${amp}" +
      s"oauth_consumer_key${equals}${sampleParams.consumerKey}${amp}" +
      s"oauth_nonce${equals}${sampleParams.nonce}${amp}" +
      s"oauth_signature_method${equals}${sampleParams.signatureMethod}${amp}" +
      s"oauth_timestamp${equals}${sampleParams.timestamp.toString}"

    assertSignatureEquals(res, expectedBaseUrl, expectedQueryParams)
  }

  @Test
  def withQueryParamWithTwoValues(): Unit = {
    val request = buildOriginalRequest(HttpMethod.GET, "/test", None, PathParams.empty, QueryParams("key" -> List("value2","value1")))
    val res = OAuth10SignatureBaseStringBuilder.build(TracingContext.dummy(), request, sampleParams, secureDomain)

    val expectedBaseUrl = s"GET&https${colonDoubleSlash}example.com${slash}test&"
    val expectedQueryParams = s"key${equals}value1${amp}key${equals}value2${amp}" +
      s"oauth_consumer_key${equals}${sampleParams.consumerKey}${amp}" +
      s"oauth_nonce${equals}${sampleParams.nonce}${amp}" +
      s"oauth_signature_method${equals}${sampleParams.signatureMethod}${amp}" +
      s"oauth_timestamp${equals}${sampleParams.timestamp.toString}"

    assertSignatureEquals(res, expectedBaseUrl, expectedQueryParams)
  }

  @Test
  def postWithXmlBody(): Unit = {
    val request = buildOriginalRequest(HttpMethod.POST, "/test", Some(Buffer.buffer(sampleXmlbody)), PathParams.empty, QueryParams.empty)
    val res = OAuth10SignatureBaseStringBuilder.build(TracingContext.dummy(), request, sampleParams, secureDomain)

    val expectedBaseUrl = s"POST&https${colonDoubleSlash}example.com${slash}test&"
    val expectedQueryParams = s"oauth_body_hash${equals}${URLEncoder.encode(sampleXmlBodyHash, "UTF-8")}${amp}" +
      s"oauth_consumer_key${equals}${sampleParams.consumerKey}${amp}" +
      s"oauth_nonce${equals}${sampleParams.nonce}${amp}" +
      s"oauth_signature_method${equals}${sampleParams.signatureMethod}${amp}" +
      s"oauth_timestamp${equals}${sampleParams.timestamp.toString}"

    assertSignatureEquals(res, expectedBaseUrl, expectedQueryParams)
  }

  @Test
  def getBodyHash(): Unit = {
    val res = OAuth10SignatureBaseStringBuilder.getBodyHash(Buffer.buffer(sampleXmlbody))
    res must be (Success(sampleXmlBodyHash))
  }

  @Ignore("not implemented")
  def postWithFormBody(): Unit = {}

  @Test
  def complexGet(): Unit = {
    val consumerKey = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    val nonce = "1111111111111111111"
    val timestamp = 1111111111
    val signature = "XXXX"
    val signatureMethod = "RSA-SHA1"
    val version = "1.0"

    val params = OAuth10Request(consumerKey, None, signature, signatureMethod, timestamp, nonce, Some(version))
    val path = "/atms/v1/atm"
    val queryParams = QueryParams(
      "Format" -> List("XML"),
      "PageOffset" -> List("0"),
      "PageLength" -> List("10"),
      "AddressLine1" -> List("70%20Main%20St"),
      "PostalCode" -> List("63366"),
      "Country" -> List("USA")
    )
    val request = buildOriginalRequest(HttpMethod.GET, path, None, PathParams.empty, queryParams)
    val domain = PublicDomainConf("sandbox.api.mastercard.com", 443, true)
    val res = OAuth10SignatureBaseStringBuilder.build(TracingContext.dummy(), request, params, domain)

    val expectedBaseUrl = s"GET&https${colonDoubleSlash}sandbox.api.mastercard.com${slash}atms${slash}v1${slash}atm&"
    val expectedQueryParams = s"AddressLine1${equals}70%2520Main%2520St${amp}" +
      s"Country${equals}USA${amp}" +
      s"Format${equals}XML${amp}" +
      s"PageLength${equals}10${amp}" +
      s"PageOffset${equals}0${amp}" +
      s"PostalCode${equals}63366${amp}" +
      s"oauth_consumer_key${equals}${consumerKey}${amp}" +
      s"oauth_nonce${equals}${nonce}${amp}" +
      s"oauth_signature_method${equals}${signatureMethod}${amp}" +
      s"oauth_timestamp${equals}${timestamp}${amp}" +
      s"oauth_version${equals}${version}"

    assertSignatureEquals(res, expectedBaseUrl, expectedQueryParams)
  }

  def buildOriginalRequest(method: HttpMethod, path: String, body: Option[Buffer], pathParams: PathParams,
                                   queryParams: QueryParams): OriginalRequest = {
    val emptyHeaders = Headers(Map[String,List[String]]())
    val relativeUri = FixedRelativeUri(UriPath(path), queryParams, PathParams(Map()))
    OriginalRequest(method, UriPath(relativeUri.path), queryParams, emptyHeaders, body, pathParams)
  }

  def assertSignatureEquals(res: OAuth10SignatureBaseStringBuilderError \/ String, baseUrl: String, queryParams: String) = res match {
    case \/-(v) => v must be (baseUrl + queryParams)
    case -\/(_) => fail("Failed to generate signature")
  }

}
