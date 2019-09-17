package com.cloudentity.edge.client

import com.cloudentity.edge.ApiGatewayTest
import io.restassured.RestAssured.given
import org.junit.Test
import org.junit.runner.RunWith
import org.scalatest.MustMatchers
import org.scalatest.junit.JUnitRunner

class TransferEncodingSpec extends ApiGatewayTest with MustMatchers {
  override def getMetaConfPath(): String = "src/test/resources/transfer-encoding/meta-config.json"

  @Test
  def shouldNotSetContentLengthHeaderWhenApiResponseHasTransferEncodignChunkedHeader(): Unit = {
    given()
    .when()
      .get("/chunked")
    .`then`()
      .header("Content-Length", null.asInstanceOf[String])
  }
}
