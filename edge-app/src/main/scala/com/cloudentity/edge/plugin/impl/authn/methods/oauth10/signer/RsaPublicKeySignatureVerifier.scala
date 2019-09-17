package com.cloudentity.edge.plugin.impl.authn.methods.oauth10.signer

import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, Signature}
import java.util.Base64

import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.vertx.core.logging.LoggerFactory
import io.vertx.core.{Future => VxFuture}
import scalaz.{-\/, \/-}

import scala.util.{Failure, Success, Try}

class RsaPublicKeySignatureVerifier extends ScalaServiceVerticle with SignatureVerifier {
  val log = LoggerFactory.getLogger(this.getClass)

  override protected def vertxServiceAddressPrefixS: Option[String] = Some(verticleId())

  override def verify(signature: Array[Byte], data: Array[Byte], key: Array[Byte]): VxFuture[SignatureVerifierError \/ Unit] = {
    Try {
      val keyFactory = KeyFactory.getInstance("RSA")
      val ks = new X509EncodedKeySpec(key)
      val pub = keyFactory.generatePublic(ks)
      log.debug(TracingContext.dummy(), pub)
      val rawSignature = Base64.getDecoder.decode(signature)
      val signerInstance = Signature.getInstance("SHA256withRSA")

      signerInstance.initVerify(pub)
      signerInstance.update(data)
      signerInstance.verify(rawSignature)
    } match {
      case Success(res) => res match {
        case true => VxFuture.succeededFuture(\/-(()))
        case false => VxFuture.succeededFuture(-\/(SignatureVerifierMismatch))
      }
      case Failure(ex) => VxFuture.succeededFuture(-\/(SignatureVerifierFailure(ex)))
    }
  }

}
