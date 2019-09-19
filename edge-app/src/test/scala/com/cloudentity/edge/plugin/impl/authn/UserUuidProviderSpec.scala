package com.cloudentity.edge.plugin.impl.authn

import io.circe.Json
import com.cloudentity.edge.domain.flow.AuthnCtx
import com.cloudentity.edge.plugin.impl.authn.entities.UserUuidProvider
import com.cloudentity.tools.vertx.scala.FutureConversions
import com.cloudentity.tools.vertx.tracing.TracingContext
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import io.vertx.core.Vertx
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

@RunWith(classOf[JUnitRunner])
class UserUuidProviderSpec extends WordSpec with MustMatchers with FutureConversions {
  implicit lazy val ec: VertxExecutionContext = VertxExecutionContext(Vertx.vertx().getOrCreateContext())

  val provider = new UserUuidProvider

  "UserUuidProvider" should {
    "read userUuid" in {
      // given
      val uuid = Json.fromString("xyz")
      val ctx = AuthnCtx("userUuid" -> uuid)

      // when
      val result = Await.result(provider.getEntity(TracingContext.dummy, ctx).toScala, 1 second)

      //then
      result mustBe(AuthnCtx("userUuid" -> uuid))
    }

    "fail when userUuid missing in ctx" in {
      // given
      val ctx = AuthnCtx()

      // when
      val result = Try(Await.result(provider.getEntity(TracingContext.dummy, ctx).toScala, 1 second))

      //then
      result.isFailure mustBe(true)
    }
  }
}
