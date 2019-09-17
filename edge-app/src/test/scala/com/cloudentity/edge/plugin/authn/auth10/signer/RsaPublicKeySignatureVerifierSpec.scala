package com.cloudentity.edge.plugin.authn.auth10.signer

import java.util.Base64

import com.cloudentity.edge.plugin.impl.authn.methods.oauth10.signer._
import com.cloudentity.edge.util.{FutureUtils, SecurityUtils}
import com.cloudentity.tools.vertx.test.VertxUnitTest
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import org.junit.{Before, Test}
import org.scalatest.MustMatchers
import scalaz.{-\/, \/-}


class RsaPublicKeySignatureVerifierSpec extends VertxUnitTest with MustMatchers with SecurityUtils with FutureUtils {
  implicit var ec: VertxExecutionContext = _

  val charset = "UTF-8"
  val sampleData = "test".getBytes(charset)
  val (publicKey, privateKey) = generateRsaKeyPairTuple

  var verifier: SignatureVerifier = _

  @Before
  def before(): Unit = {
    ec = VertxExecutionContext(vertx.getOrCreateContext())
    verifier = new RsaPublicKeySignatureVerifier()
  }

  @Test
  def verifySignature(): Unit = {
    val signature = Base64.getEncoder.encode(rsaSign(sampleData, privateKey))
    val res = await(verifier.verify(signature, sampleData, publicKey.getEncoded))
    res must be(\/-(()))
  }

  @Test
  def verifySignatureMismatch(): Unit = {
    val privateKey = loadPrivateKey("src/test/resources/plugin/authn/oauth10/sample-rsa.key")
    val signature = Base64.getEncoder.encode(rsaSign(sampleData, privateKey))

    val res = await(verifier.verify(signature, sampleData, publicKey.getEncoded))
    res must be(-\/(SignatureVerifierMismatch))
  }

  @Test
  def verifySignatureError(): Unit = {
    val signature = "signature".getBytes()

    val res = await(verifier.verify(signature, sampleData, "invalidKey".getBytes(charset)))
    res match {
      case -\/(SignatureVerifierFailure(_)) =>
      case _ => fail()
    }
  }

}