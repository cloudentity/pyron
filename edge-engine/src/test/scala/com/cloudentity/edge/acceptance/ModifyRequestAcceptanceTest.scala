package com.cloudentity.edge.acceptance

import com.cloudentity.edge.EdgeAcceptanceTest
import io.circe.Decoder
import com.cloudentity.edge.domain.flow.{PluginName, RequestCtx}
import com.cloudentity.edge.plugin.config._
import com.cloudentity.edge.domain.http.ApiResponse
import com.cloudentity.edge.plugin.RequestPluginService
import com.cloudentity.edge.plugin.verticle.RequestPluginVerticle
import com.cloudentity.edge.util.MockUtils
import io.restassured.RestAssured._
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpResponse.response

import scala.concurrent.Future

class ModifyResponseAcceptanceSpec extends EdgeAcceptanceTest with MockUtils {
  override def getMetaConfPath(): String = "src/test/resources/acceptance/modify-response/meta-config-test.json"

    var targetService: ClientAndServer = null

    @Before
  def before(): Unit = {
      targetService = startClientAndServer(8201)
    }

    @After
  def finish(): Unit = {
      targetService.stop
    }

    @Test
  def shouldModifyApiResponseInRequestPlugin(): Unit = {
      mockOnPath(targetService)("/modify-response", response().withStatusCode(200))

        given()
      .when()
        .get("/modify-response")
      .`then`()
        .statusCode(204)
    }
}

class ModifyingRequestPlugin extends RequestPluginVerticle[Unit] with RequestPluginService {
    def confDecoder: Decoder[Unit] = Decoder.decodeUnit
    def validate(conf: Unit): ValidateResponse = ValidateOk
    def name: PluginName = PluginName("modify-response")

    def apply(ctx: RequestCtx, conf: Unit): Future[RequestCtx] = {
      val mod: ApiResponse => ApiResponse =
          _.copy(statusCode = 204)

        Future.successful(ctx.withModifyResponse(mod))
    }
}
