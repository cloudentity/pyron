package com.cloudentity.edge.acceptance

import com.cloudentity.edge.EdgeAcceptanceTest
import io.restassured.RestAssured.given
import org.junit.Test
import org.scalatest.MustMatchers

class TransferEncodingAcceptanceTest extends EdgeAcceptanceTest with MustMatchers {
  override def getMetaConfPath(): String = "src/test/resources/acceptance/transfer-encoding/meta-config.json"

  @Test
  def shouldNotSetContentLengthHeaderWhenApiResponseHasTransferEncodignChunkedHeader(): Unit = {
    given()
    .when()
      .get("/chunked")
    .`then`()
      .header("Content-Length", null.asInstanceOf[String])
  }
}
