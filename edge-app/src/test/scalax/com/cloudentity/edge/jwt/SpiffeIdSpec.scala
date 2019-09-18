package com.cloudentity.edge.jwt

import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SpiffeIdSpec extends WordSpec with MustMatchers {
  "SpiffeId" should {
    "be parsed from string" in {
      val spiffeIdString = "spiffe://cluster.local/ns/default/sa/default/ver/1/ins/1"
      val sid = SpiffeId.fromString(spiffeIdString)
      sid must be ('right)

      val spiffe = sid.right.get
      spiffe.domain must be ("cluster.local")
      spiffe.namespace must be ("default")
      spiffe.serviceAccount must be ("default")
      spiffe.versionId must be ("1")
      spiffe.instanceId must be ("1")

      spiffe.toString must be (spiffeIdString)
    }
  }
}
