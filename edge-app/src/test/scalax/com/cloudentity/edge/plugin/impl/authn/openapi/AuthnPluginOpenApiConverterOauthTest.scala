package com.cloudentity.edge.plugin.impl.authn.openapi

import AuthnPluginOpenApiTestTools._
import com.cloudentity.edge.plugin.impl.authn._
import com.cloudentity.edge.util.OpenApiTestUtils
import io.swagger.models.auth.OAuth2Definition
import io.vertx.core.http.HttpMethod
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{Inside, Matchers, WordSpec}

import scala.collection.JavaConverters._

@RunWith(classOf[JUnitRunner])
class AuthnPluginOpenApiConverterOauthTest extends WordSpec with Matchers with OpenApiTestUtils with Inside  {

  val baseEndpointConf: AuthnPluginConf = AuthnPluginConf(
    methods = List("authorizationCodeOAuth"),
    entities = None,
    optionalEntities = None,
    tokenHeader = None,
    ctxKey = None
  )

  val oauthConf = OpenApiOauthUrlsConf(
    authorizationUrl = OauthUrl("localhost", "/oauth/authorize", 80, ssl = false),
    tokenUrl = OauthUrl("localhost", "/oauth/token", 80, ssl = false)
  )

  val pluginConf = AuthnApiOpenApiConf(
    Some(oauthConf), Map("authorizationCodeOAuth" -> Oauth2SecurityDefinitionConf(List(ImplicitFlow)))
  )

  "Authn Plugin OpenAPI Converter" should {

    "append oauth to security definitions" in {
      val resp = convertWithSingleGetEndpoint(baseEndpointConf, pluginConf)
      resp securityDefinitionsShould (contain key "oauth2_implicit")
    }

    "set proper flows in oauth security definitions" in {
      val resp = convertWithSingleGetEndpoint(baseEndpointConf, pluginConf)
      inside(resp.getSecurityDefinitions.get("oauth2_implicit")) {
        case Some(oauthDef: OAuth2Definition) => {
          oauthDef.getFlow should be ("implicit")
        }
        case _ => fail("Expected Oauth2Definition object")
      }
    }

    "add multiple oauth2 compatible security definitions suffixed with flow name when multiple flows are configured for the same authn method" in {
      val resp = convertWithSingleGetEndpoint(baseEndpointConf,
        AuthnApiOpenApiConf(
          Some(oauthConf),
          Map("authorizationCodeOAuth" -> Oauth2SecurityDefinitionConf(List(ImplicitFlow, AuthorizationCodeFlow)))
        )
      )
      resp securityDefinitionsShould (contain key "oauth2_implicit")
      resp securityDefinitionsShould (contain key "oauth2_authorizationCode")

      inside(resp.getSecurityDefinitions.get("oauth2_implicit")) {
        case Some(oauthDef: OAuth2Definition) => oauthDef.getFlow should be ("implicit")
        case _ => fail("Expected Oauth2Definition object")
      }

      inside(resp.getSecurityDefinitions.get("oauth2_authorizationCode")) {
        case Some(oauthDef: OAuth2Definition) => oauthDef.getFlow should be ("accessCode")
        case _ => fail("Expected Oauth2Definition object")
      }
    }

    "add multiple oauth2 compatible security definitions suffixed with flow name when multiple authn methods are configured" in {
      val resp = convertWithSingleGetEndpoint(baseEndpointConf.copy(
        methods = List("authorizationCodeOAuth", "someSampleAuthnMethod")),
        AuthnApiOpenApiConf(
          Some(oauthConf),
          Map(
            "authorizationCodeOAuth" -> Oauth2SecurityDefinitionConf(List(ImplicitFlow)),
            "someSampleAuthnMethod" -> Oauth2SecurityDefinitionConf(List(AuthorizationCodeFlow))
          )
        )
      )
      resp securityDefinitionsShould (contain key "oauth2_implicit")
      resp securityDefinitionsShould (contain key "oauth2_authorizationCode")
      resp securityDefinitionsShould (have size 2)
    }

    "add only one security definition when the same configuration is used on two endpoints" in {
      val firstEndpoint = SimpleTestEndpoint("/test", HttpMethod.GET)
      val secondEndpoint = SimpleTestEndpoint("/test2", HttpMethod.POST)
      val swagger = swaggerWithEndpoints(List(firstEndpoint, secondEndpoint))

      val responses = convertMultipleEndpoints(swagger,
        List(
          (apiRule(firstEndpoint, baseEndpointConf), baseEndpointConf),
          (apiRule(secondEndpoint, baseEndpointConf), baseEndpointConf)
        ),
        pluginConf
      )

      responses.last securityDefinitionsShould (have size 1)
    }

    "add multiple oauth definitions and security schemes when two endpoints with two different methods are used" in {
      val firstEndpoint = SimpleTestEndpoint("/test", HttpMethod.GET)
      val secondEndpoint = SimpleTestEndpoint("/test2", HttpMethod.POST)

      val altPluginConf  = AuthnApiOpenApiConf(
        Some(oauthConf),
        Map(
          "authorizationCodeOAuth" -> Oauth2SecurityDefinitionConf(List(ImplicitFlow)),
          "someSampleAuthnMethod" -> Oauth2SecurityDefinitionConf(List(AuthorizationCodeFlow))
        )
      )

      val altEndpointConf = baseEndpointConf.copy(methods = List("someSampleAuthnMethod"))
      val swagger = swaggerWithEndpoints(List(firstEndpoint, secondEndpoint))

      val responses = convertMultipleEndpoints(swagger,
        List(
          (apiRule(firstEndpoint, baseEndpointConf), baseEndpointConf),
          (apiRule(secondEndpoint, altEndpointConf), altEndpointConf)
        ),
        altPluginConf
      )

      responses.last securityDefinitionsShould (have size 2)
    }

    "not add anything to security definitions when there are no configured methods, despite mappings being defined" in {
      val resp = convertWithSingleGetEndpoint(baseEndpointConf.copy(methods = List()), pluginConf)
      resp securityDefinitionsShould be (null)
    }

    "add security scheme to endpoint definition" in {
      val params = singleEndpointParams(baseEndpointConf, pluginConf)

      val resp = convertWithParams(params)

      val op = resp.getOperationByRule(params._2)
      val sec = op.getSecurity.asScala
      sec should not be empty
      sec.filter(_.containsKey("oauth2_implicit")) should not be empty
    }

    "add multiple security scheme to endpoint definition" in {
      val params = singleEndpointParams(baseEndpointConf, AuthnApiOpenApiConf(
        Some(oauthConf),
        Map("authorizationCodeOAuth" -> Oauth2SecurityDefinitionConf(List(ImplicitFlow, AuthorizationCodeFlow)))
      ))
      val resp = convertWithParams(params)

      val sec = resp.getOperationByRule(params._2).getSecurity.asScala
      sec.filter(_.containsKey("oauth2_implicit")) should not be empty
      sec.filter(_.containsKey("oauth2_authorizationCode")) should not be empty
    }

    "set urls for implicit flow" in {
      expectedOauthUrlsForFlow(ImplicitFlow, "http://localhost/oauth/authorize", null)
    }

    "set urls for authorization code flow" in {
      expectedOauthUrlsForFlow(AuthorizationCodeFlow, "http://localhost/oauth/authorize", "http://localhost/oauth/token")
    }

    "set urls for client credentials code flow" in {
      expectedOauthUrlsForFlow(ClientCredentialsFlow, null, "http://localhost/oauth/token")
    }

    "set urls for password code flow" in {
      expectedOauthUrlsForFlow(PasswordGrantFlow, null, "http://localhost/oauth/token")
    }

  def expectedOauthUrlsForFlow(flow: OpenApiOauth2Flow, authorizationUrl: String, tokenUrl: String): Unit = {
    val resp = convertWithSingleGetEndpoint(baseEndpointConf,
      AuthnApiOpenApiConf(
        Some(oauthConf),
        Map("authorizationCodeOAuth" -> Oauth2SecurityDefinitionConf(List(flow)))
      )
    )
    inside(resp.getSecurityDefinitions.get(s"oauth2_${flow.name}")) {
      case Some(oauthDef: OAuth2Definition) => {
        oauthDef.getAuthorizationUrl should be (authorizationUrl)
        oauthDef.getTokenUrl should be (tokenUrl)
      }
      case _ => fail("Expected Oauth2Definition object")
    }
  }
  }

}


