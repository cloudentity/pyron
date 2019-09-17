package com.cloudentity.edge

import com.cloudentity.edge.jwt.{JwtService, JwtServiceFactory, Keystore}
import com.cloudentity.edge.util.{FutureUtils, JwtUtils, MockUtils}
import io.restassured.RestAssured.given
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonObject
import org.hamcrest.core.IsEqual
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.scalatest.MustMatchers

class SidecarAcceptanceSpec extends ApiGatewayTest with MustMatchers with MockUtils with JwtUtils with FutureUtils {
  var targetService: ClientAndServer = _
  var vaultService: ClientAndServer = _

  @Before
  def before(): Unit = {
    targetService = startClientAndServer(7760)
    vaultService = startClientAndServer(9988)
  }

  @After
  def finish(): Unit = {
    targetService.stop
    vaultService.stop()
  }

  override def getMetaConfPath(): String = "src/test/resources/sidecar/meta-config.json"

  lazy val jwtService: JwtService = JwtServiceFactory.createClient(getVertx, "asymmetric")
  val keystore = Keystore("src/test/resources/keystore.jks", None, "app", "password")

  @Test
  def shouldParseJwtUsingPublicKeyOfThisMicroserviceWhenHandlingRequestFromOtherMicroservice(): Unit = {

      mockVaultServiceResponse(vaultService)("aa:bb:cc:dd:ee", vaultKeyMockedBody)
      mockOnPathWithPongingBodyAndHeaders(targetService)("/authn-with-jwt", 201)

      val token = await(jwtService.empty()).put("method", "rsa")
      val auth = await(jwtService.sign(token))

      given()
        .when()
        .header("Authorization", s"Bearer $auth")
        .get("/service/authn-with-jwt")
        .`then`()
        .statusCode(201)
        .header(
          HttpHeaders.AUTHORIZATION.toString,
          asJwtJson(jwtService).andThen(_.getJsonObject("content"))(_),
          IsEqual.equalTo(new JsonObject("""
          {
            "method": "rsa",
            "tokenType":"jwt",
            "authnMethod" :"jwt"
          }
          """))
        )

  }

  val vaultKeyMockedBody = """{"request_id":"5da81774-4741-e3e3-5cf4-797570391c59","lease_id":"","renewable":false,"lease_duration":0,"data":{"certificate":"-----BEGIN CERTIFICATE-----\nMIID4zCCAsugAwIBAgIUfcPybsfenlyzaCE45MfWQh8rTcEwDQYJKoZIhvcNAQEL\nBQAwFjEUMBIGA1UEAxMLbXl2YXVsdC5jb20wHhcNMTcxMjEyMTY0NzQ2WhcNMTcx\nMjE5MTY0ODE2WjAsMSowKAYDVQQDEyExLmV4YW1wbGUtc2VydmljZS5jbG91ZGVu\ndGl0eS5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDWzMthpE/P\nnNOluZ2iKRAEcjlT7j3z5XDH/eh8n+/ynh/+xKkE7z+l7tdklgApJe8M6oyXGlJ8\n470qVD+vNLo/vK2GrYD85pZ7Q8ZT/wA1gZIOkxW5GM2ET6mEjIIKIa/DLqhzWwwt\nLwBUGyxUqbViyLRNlAwu/Foj2cTIiZso4iEQzGQkydfxCeNrN9PeZLehE4QLtuDc\nXvTr6+WPBBA6+VGsqoUMbLBjIfVzrAFuCS7NTnanuwjVkQvTJIyymayr1141yIET\nAguwCuWI9ote/eIbtAUNQOBB5Sigax7LEyLzyTbrfwLO260porNC4z1XrXjyPQG4\nZ4SQncwzcrNJAgMBAAGjggERMIIBDTAOBgNVHQ8BAf8EBAMCA6gwHQYDVR0lBBYw\nFAYIKwYBBQUHAwEGCCsGAQUFBwMCMB0GA1UdDgQWBBTOThdQIN7GEhx9fxK5Q2ry\nVtIY6zAfBgNVHSMEGDAWgBTwo0FL1iCBXwjneFcJH2xXVOSZQzA7BggrBgEFBQcB\nAQQvMC0wKwYIKwYBBQUHMAKGH2h0dHA6Ly8xMjcuMC4wLjE6ODIwMC92MS9wa2kv\nY2EwLAYDVR0RBCUwI4IhMS5leGFtcGxlLXNlcnZpY2UuY2xvdWRlbnRpdHkuY29t\nMDEGA1UdHwQqMCgwJqAkoCKGIGh0dHA6Ly8xMjcuMC4wLjE6ODIwMC92MS9wa2kv\nY3JsMA0GCSqGSIb3DQEBCwUAA4IBAQBEw8JEVbUao99Y5pQdZSlNdByKAw/sfirj\n0xi5y+N1RHQDVbZn9sg7A7BqtZyZjg/Mvbm7hRUjOi+CfTbg1pEogLNiWcxA5fNz\nFRS11xojnY9hX1E8+Imaantw6lUHgBl57vMlT5P5Ki1rlU2RLXX9TlsEUUctf0pu\nUYR9QPsBDQJc5rFMV7ChBEFex2YdjRqx3til0FUQyhvJxxmO+JfloLPZx2b9JD/k\nzJ2NJN2sz+BIW7KKlbSX0R1HbSKhsW3I5U628T9PV1GxzV9uY3JHFUqPr9ZjyK6W\nnGFo9r8l9zAWE5o5UCxeTwKcySAjbJ2AYkBwwMTjR1kLV4KqEQOS\n-----END CERTIFICATE-----\n","revocation_time":0},"wrap_info":null,"warnings":null,"auth":null}"""
}
