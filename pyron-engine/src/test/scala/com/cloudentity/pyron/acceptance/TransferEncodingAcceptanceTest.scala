package com.cloudentity.pyron.acceptance

import com.cloudentity.pyron.PyronAcceptanceTest
import io.restassured.RestAssured.given
import org.hamcrest.Matchers
import org.junit.Test
import org.scalatest.MustMatchers

class TransferEncodingAcceptanceTest extends PyronAcceptanceTest with MustMatchers {
  override def getMetaConfPath: String = "src/test/resources/acceptance/transfer-encoding/meta-config.json"

  @Test
  def shouldNotSetContentLengthHeaderWhenApiResponseHasTransferEncodignChunkedHeader(): Unit = {
    given()
    .when()
      .get("/chunked")
    .`then`()
      .header("Content-Length", Matchers.nullValue())
  }
}
