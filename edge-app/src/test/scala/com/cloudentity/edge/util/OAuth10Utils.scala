package com.cloudentity.edge.util


case class OAuth10Request(consumerKey: Option[String], token: Option[String], signature: Option[String], signatureMethod: Option[String],
                          timestamp: Option[String], nonce: Option[String], version: Option[String]) {

  def withMethod(method: String) = this.copy(signatureMethod = Some(method))

}

trait OAuth10Utils {

  def buildAuthorizationHeader(request: OAuth10Request) = {
    val sb = new StringBuilder
    sb.append("OAuth ")
    request.consumerKey.map(v => sb.append(s"""oauth_consumer_key="${v}""""))
    request.signature.map(v => sb.append(s""",oauth_signature="${v}""""))
    request.signatureMethod.map(v => sb.append(s""",oauth_signature_method="${v}""""))
    request.timestamp.map(v => sb.append(s""",oauth_timestamp="${v}""""))
    request.nonce.map(v => sb.append(s""",oauth_nonce="${v}""""))
    request.token.map(v => sb.append(s""",oauth_token="${v}""""))
    request.version.map(v => sb.append(s""",oauth_version="${v}""""))
    sb.toString()
  }

}

object OAuth10Data {
  val sampleRequest = OAuth10Request(Some("clientId!keyId"), None, Some("signature123"), Some("RSA-SHA1"), Some("1532357727"), Some("nonce123"), None)
  val emptyRequest = OAuth10Request(None, None, None, None, None, None, None)
}