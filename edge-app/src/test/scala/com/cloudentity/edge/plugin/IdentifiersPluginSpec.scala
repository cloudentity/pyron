package com.cloudentity.edge.plugin

import com.cloudentity.edge.ApiGatewayTest
import com.cloudentity.edge.util.{JwtUtils, MockUtils, SecurityUtils}
import io.restassured.RestAssured.given
import io.vertx.core.json.JsonObject
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpResponse.response
import org.scalatest.MustMatchers

class IdentifiersPluginSpec extends ApiGatewayTest with MustMatchers
  with MockUtils with JwtUtils with SecurityUtils {

  var targetService: ClientAndServer = _
  var oidcService: ClientAndServer = _

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
    oidcService = startClientAndServer(9987)
  }

  @After
  def finish(): Unit = {
    targetService.stop
    oidcService.stop()
  }

  @Test
  def whenRequestComesInWithOAuthAccessTokenWhenAccesTokenIsInvalid(): Unit = {
      val jwkResponse = response().withStatusCode(200).withBody("{\"keys\":[]}")
      mockOnPath(oidcService)("/oauth/jwk", jwkResponse)

      val devicesResponse = response().withStatusCode(200).withBody("{\"devices\":[]}")
      mockOnPath(targetService)("/devices", devicesResponse)

      val accessToken = "eyJraWQiOiJyc2ExIiwiYWxnIjoiUlMyNTYifQ.e30.iNJ9CT8LgSsH6NGfW1_F-WCod6qayzdVUtHwzUS_se2u2MsYoGJf6CS1H8ZdV8mW6dWgv36S2nTa9i9s-7Lo29-1UmCcPGs5FidZ38psyriykkySni-VBsgeODQLQBUxkhBq7nWPlfpGGBJTD0DIA5XEfeudbZFpuLoTeVANatfTawe83ZtLs8sZjVw7-iA6bGkIV5Fj-mSXwx8vsXUcp6bgmLf5vFrBbxSi87x9m9x5T5B1z9SaJJT2OpEtGMWWHX9lulyJGffstkzTf0UNzJz6CsTmaUvnLCFtQPUa5QmIPj_oSmHADkA3Rgma-9-G2Bc43e-ay9Wquewnim_udQ"

      given()
        .header("Authorization", "Bearer " + accessToken)
      .when()
        .get("/service/devices")
      .`then`()
        .statusCode(400)

  }

  @Test
  def whenRequestComesInWithOAuthAccessTokenWhenAccesTokenIsValid(): Unit = {
      val kid = "rsa1"
      val keyPair = generateRsaKeyPair
      val jwkSet = toJwkSet(generateRsaJwk(keyPair, kid))

      val uuid = "123-456-789"
      val payload = new JsonObject().put("sub", uuid)
      val accessToken = generateRsaSignedJwt(keyPair, kid, payload)

      val jwkResponse = response().withStatusCode(200).withBody(jwkSet.toString)
      mockOnPath(oidcService)("/oauth/jwk", jwkResponse)
      Thread.sleep(1000) // time for oidc client to reload

      val devicesResponse = response().withStatusCode(200).withBody("{\"devices\":[]}")
      mockOnPath(targetService)("/devices", devicesResponse)

      given()
        .header("Authorization", "Bearer " + accessToken)
      .when()
        .get("/service/devices")
      .`then`()
        .statusCode(200)
  }
}
