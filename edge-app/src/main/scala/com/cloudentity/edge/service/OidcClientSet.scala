package com.cloudentity.edge.service

import com.nimbusds.jose.jwk.JWKSet
import com.cloudentity.edge.util.ConfigDecoder
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle

case class OidcClientSetConfig(clientPrefixes: List[String])

@Deprecated
class OidcClientSet extends ScalaServiceVerticle with OidcClient with ConfigDecoder {
  var oidcClientSetConfig: OidcClientSetConfig = _
  var clients: List[OidcClient] = _

  override def initService(): Unit = {
    import io.circe.generic.auto._
    oidcClientSetConfig = decodeConfigUnsafe[OidcClientSetConfig]
    clients = oidcClientSetConfig.clientPrefixes.map(prefix => createClient(classOf[OidcClient], prefix))
  }

  override def getPublicKeys(): VxFuture[OidcClientError \/ JWKSet] =
    clients.traverseOperations(_.getPublicKeys().toOperation).map(flattenSets).run.toJava()

  def flattenSets(sets: List[JWKSet]): JWKSet =
    sets.foldLeft(new JWKSet())((set, s) => {
      set.getKeys.addAll(s.getKeys)
      set
    })
}
