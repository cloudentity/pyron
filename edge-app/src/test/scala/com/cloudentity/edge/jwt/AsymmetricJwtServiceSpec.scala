package com.cloudentity.edge.jwt

import java.security.cert.X509Certificate

import com.cloudentity.edge.jwt.impl.{AsymmetricJwtConf, AsymmetricJwtService}
import com.cloudentity.edge.util.{FutureUtils, JwtUtils}
import com.cloudentity.tools.vertx.bus.VertxBus
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import com.cloudentity.tools.vertx.test.VertxUnitTest
import io.vertx.core.{Future, Vertx}
import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AsymmetricJwtServiceSpec extends WordSpec with MustMatchers with JwtUtils with FutureUtils {

  lazy val vertx: Vertx = Vertx.vertx()
  implicit lazy val ec = VertxExecutionContext(vertx.getOrCreateContext())
  VertxBus.registerPayloadCodec(vertx.eventBus())

  val conf = AsymmetricJwtConf(
    "aa:bb:cc:dd:ee",
    Some("service-a"),
    Some("1234"),
    None,
    java.time.Duration.ofSeconds(3600),
    None,
    None,
    Some(Keystore(
      "src/test/resources/keystore.jks",
      None,
      "app",
      "password"
    )),
    None
  )

  val service = new AsymmetricJwtService

  service.setExecutionContext(ec)
  service.initialize(conf,
    (jwtCommonName: String, cert: X509Certificate) => Future.succeededFuture(true),
    (kid: String) => Future.succeededFuture(Some(conf.keystore.get.readCertificate().get))
  )

  "AsymmetricJwtServiceSpec" should {
    "sign and parse token" in {
      val token = await(service.empty())
      val signed = await(service.sign(token))
      val decoded = await(service.parse(signed))

      decoded must be (token)
    }
  }
}
