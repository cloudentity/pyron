package com.cloudentity.edge.plugin.authn

import com.cloudentity.edge.ApiGatewayTest
import com.cloudentity.edge.domain.flow.PluginName
import com.cloudentity.edge.plugin.impl.authn.AuthnEdgePlugin
import io.restassured.RestAssured.given
import org.junit.Test

class AuthnEdgeWithCloudIntegrationSpec extends ApiGatewayTest {
  override def getMetaConfPath(): String = "src/test/resources/authn-edge-cloud/meta-config.json"

  @Test
  def authnEdgePluginShouldProxyTheRequestToAuthnCloudPluginAndAuthenticate(): Unit = {
    given()
    .when()
      .header("authorization", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.t-IDcSemACt8x4iTMCda8Yhe3iZaWbvV5XKSTbuAn0M")
      .get("/authn-edge")
    .`then`()
      .statusCode(200)
  }
}

class TestAuthnEdgePlugin extends AuthnEdgePlugin {
  override def name: PluginName = PluginName("authn-edge")
}