package com.cloudentity.edge.plugin.impl.authn.openapi

import AuthnPluginOpenApiTestTools._
import com.cloudentity.edge.plugin.impl.authn._
import com.cloudentity.edge.util.OpenApiTestUtils
import io.swagger.models.auth.{ApiKeyAuthDefinition, In}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{Inside, Matchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class AuthnPluginOpenApiConverterSsoTokenTest extends WordSpec with Matchers with OpenApiTestUtils with Inside  {

  val baseEndpointConf: AuthnPluginConf = AuthnPluginConf(
    methods = List("sso"),
    entities = None,
    optionalEntities = None,
    tokenHeader = None,
    ctxKey = None
  )

  val pluginConf = AuthnApiOpenApiConf(
    None,
    Map("sso" -> ApiKeySecurityDefinitionConf("ssoTokenDef", Header, "ssoToken"))
  )

  "Authn Plugin OpenAPI Converter" should {

    "append sso token to security definitions" in {
      val resp = convertWithSingleGetEndpoint(baseEndpointConf, pluginConf)
      resp securityDefinitionsShould (contain key "ssoTokenDef")
    }

    "set proper place and token name for sso token" in {
      val resp = convertWithSingleGetEndpoint(baseEndpointConf, pluginConf)
      inside(resp.getSecurityDefinitions.get("ssoTokenDef")) {
        case Some(apiKeyDef: ApiKeyAuthDefinition) => {
          apiKeyDef.getIn should be (In.HEADER)
          apiKeyDef.getName should be ("ssoToken")
        }
        case _ => fail("Expected ApiKeyAuthDefinition object")
      }
    }

  }

}


