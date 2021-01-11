package com.cloudentity.pyron.rule

import java.util.UUID

import io.circe.generic.semiauto._
import io.circe.{Decoder, Json}
import com.cloudentity.pyron.VertxSpec
import com.cloudentity.pyron.plugin.config._
import com.cloudentity.pyron.domain.flow.{EndpointMatchCriteria, PathMatching, PathPrefix, ApiGroupPluginConf, PluginName, RequestCtx, ResponseCtx, StaticServiceRule, TargetHost}
import com.cloudentity.pyron.domain.rule.{ExtRuleConf, RequestPluginsConf, ResponsePluginsConf, RuleConf, RuleConfWithPlugins}
import com.cloudentity.pyron.plugin.verticle.{RequestPluginVerticle, ResponsePluginVerticle}
import com.cloudentity.pyron.rule.RuleBuilder.InvalidPluginConf
import com.cloudentity.tools.vertx.tracing.internals.JaegerTracing
import io.vertx.core.http.HttpMethod
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scalaz.{Failure, NonEmptyList, Success, ValidationNel}

@RunWith(classOf[JUnitRunner])
class RuleBuilderSpec extends WordSpec with MustMatchers with VertxSpec {

  case class RequestConfig(x: String, y: String)
  class RequestPlugin extends RequestPluginVerticle[RequestConfig] {
    val _name = UUID.randomUUID().toString
    override def name: PluginName = PluginName(_name)
    override def apply(ctx: RequestCtx, conf: RequestConfig): Future[RequestCtx] = ???
    override def validate(c: RequestConfig): ValidateResponse = ???
    override def confDecoder: Decoder[RequestConfig] = deriveDecoder
  }

  case class ResponseConfig(x: String, y: String)
  class ResponsePlugin extends ResponsePluginVerticle[ResponseConfig] {
    val _name = UUID.randomUUID().toString
    override def name: PluginName = PluginName(_name)
    override def apply(ctx: ResponseCtx, conf: ResponseConfig): Future[ResponseCtx] = ???
    override def validate(c: ResponseConfig): ValidateResponse = ???
    override def confDecoder: Decoder[ResponseConfig] = deriveDecoder
  }

  def pluginConf(pluginName: PluginName) = ApiGroupPluginConf(pluginName, Json.fromFields(List("x" -> Json.fromString("x"), "y" -> Json.fromString("y"))), None)

  "RuleBuilder.build" should {
    val ruleConf = RuleConf(None, EndpointMatchCriteria(HttpMethod.GET, PathMatching("".r, Nil, PathPrefix(""), "")), StaticServiceRule(TargetHost(""), 9000, false), true, None, None, None, None, Nil, None, None, None, ExtRuleConf(None))
    "build Rule without plugins" in {
      // given
      val withPlugins = RuleConfWithPlugins(ruleConf, RequestPluginsConf(Nil, Nil, Nil), ResponsePluginsConf(Nil, Nil, Nil))

      // when
      val result: ValidationNel[InvalidPluginConf, Rule] =
        Await.result(RuleBuilder.build(vertx, JaegerTracing.noTracing, withPlugins), 3 seconds)

      // then
      result must be(Success(Rule(ruleConf, Nil, Nil)))
    }

    "build Rule with valid request and response plugins" in {
      // given
      val reqPlugin = new RequestPlugin() {
        override def validate(c: RequestConfig): ValidateResponse = ValidateOk
      }

      val respPlugin = new ResponsePlugin() {
        override def validate(c: ResponseConfig): ValidateResponse = ValidateOk
      }

      deployVerticle(reqPlugin)
      deployVerticle(respPlugin)

      val reqConf = pluginConf(reqPlugin.name)
      val respConf = pluginConf(respPlugin.name)
      val withPlugins = RuleConfWithPlugins(ruleConf,
        RequestPluginsConf(List(reqConf), List(reqConf), List(reqConf)),
        ResponsePluginsConf(List(respConf), List(respConf), List(respConf))
      )

      // when
      val result: ValidationNel[InvalidPluginConf, Rule] =
        Await.result(RuleBuilder.build(vertx, JaegerTracing.noTracing, withPlugins), 3 seconds)

      // then
      result match {
        case Success(rule) =>
          rule.requestPlugins.size must be(3)
          rule.responsePlugins.size must be(6)
        case x => fail(x.toString)
      }
    }

    "fail when valid request plugin but invalid endpoint response plugin" in {
      // given
      val reqPlugin = new RequestPlugin() {
        override def validate(c: RequestConfig): ValidateResponse = ValidateOk
      }

      val respPlugin = new ResponsePlugin() {
        override def validate(c: ResponseConfig): ValidateResponse = ValidateError("errorResp")
      }

      deployVerticle(reqPlugin)
      deployVerticle(respPlugin)

      val reqConf = pluginConf(reqPlugin.name)
      val respConf = pluginConf(respPlugin.name)
      val withPlugins = RuleConfWithPlugins(ruleConf, RequestPluginsConf(Nil, List(reqConf), Nil), ResponsePluginsConf(Nil, List(respConf), Nil))

      // when
      val result: ValidationNel[InvalidPluginConf, Rule] =
        Await.result(RuleBuilder.build(vertx, JaegerTracing.noTracing, withPlugins), 3 seconds)

      // then
      result match {
        case Success(rule)   => fail(rule.toString)
        case Failure(errors) => errors must be(NonEmptyList(InvalidPluginConf(respConf, "errorResp")))
      }
    }

    "fail when invalid pre response plugin" in {
      // given
      val respPlugin = new ResponsePlugin() {
        override def validate(c: ResponseConfig): ValidateResponse = ValidateError("errorResp")
      }

      deployVerticle(respPlugin)

      val respConf = pluginConf(respPlugin.name)
      val withPlugins = RuleConfWithPlugins(ruleConf, RequestPluginsConf(Nil, Nil, Nil), ResponsePluginsConf(List(respConf), Nil, Nil))

      // when
      val result: ValidationNel[InvalidPluginConf, Rule] =
        Await.result(RuleBuilder.build(vertx, JaegerTracing.noTracing, withPlugins), 3 seconds)

      // then
      result match {
        case Success(rule)   => fail(rule.toString)
        case Failure(errors) => errors must be(NonEmptyList(InvalidPluginConf(respConf, "errorResp")))
      }
    }

    "fail when invalid post response plugin" in {
      // given
      val respPlugin = new ResponsePlugin() {
        override def validate(c: ResponseConfig): ValidateResponse = ValidateError("errorResp")
      }

      deployVerticle(respPlugin)

      val respConf = pluginConf(respPlugin.name)
      val withPlugins = RuleConfWithPlugins(ruleConf, RequestPluginsConf(Nil, Nil, Nil), ResponsePluginsConf(Nil, Nil, List(respConf)))

      // when
      val result: ValidationNel[InvalidPluginConf, Rule] =
        Await.result(RuleBuilder.build(vertx, JaegerTracing.noTracing, withPlugins), 3 seconds)

      // then
      result match {
        case Success(rule)   => fail(rule.toString)
        case Failure(errors) => errors must be(NonEmptyList(InvalidPluginConf(respConf, "errorResp")))
      }
    }

    "fail when invalid endpoint request plugin but valid response plugin" in {
      // given
      val reqPlugin = new RequestPlugin() {
        println("req", name)
        override def validate(c: RequestConfig): ValidateResponse = ValidateError("errorReq")
      }

      val respPlugin = new ResponsePlugin() {
        println("res", name)
        override def validate(c: ResponseConfig): ValidateResponse = ValidateOk
      }

      deployVerticle(reqPlugin)
      deployVerticle(respPlugin)

      val reqConf = pluginConf(reqPlugin.name)
      val respConf = pluginConf(respPlugin.name)
      val withPlugins = RuleConfWithPlugins(ruleConf, RequestPluginsConf(Nil, List(reqConf), Nil), ResponsePluginsConf(Nil, List(respConf), Nil))

      // when
      val result: ValidationNel[InvalidPluginConf, Rule] =
        Await.result(RuleBuilder.build(vertx, JaegerTracing.noTracing, withPlugins), 3 seconds)

      // then
      result match {
        case Success(rule)   => fail(rule.toString)
        case Failure(errors) => errors must be(NonEmptyList(InvalidPluginConf(reqConf, "errorReq")))
      }
    }

    "fail when invalid pre request plugin" in {
      // given
      val reqPlugin = new RequestPlugin() {
        println("req", name)
        override def validate(c: RequestConfig): ValidateResponse = ValidateError("errorReq")
      }

      deployVerticle(reqPlugin)

      val reqConf = pluginConf(reqPlugin.name)
      val withPlugins = RuleConfWithPlugins(ruleConf, RequestPluginsConf(List(reqConf), Nil, Nil), ResponsePluginsConf(Nil, Nil, Nil))

      // when
      val result: ValidationNel[InvalidPluginConf, Rule] =
        Await.result(RuleBuilder.build(vertx, JaegerTracing.noTracing, withPlugins), 3 seconds)

      // then
      result match {
        case Success(rule)   => fail(rule.toString)
        case Failure(errors) => errors must be(NonEmptyList(InvalidPluginConf(reqConf, "errorReq")))
      }
    }

    "fail when invalid post request plugin" in {
      // given
      val reqPlugin = new RequestPlugin() {
        println("req", name)
        override def validate(c: RequestConfig): ValidateResponse = ValidateError("errorReq")
      }

      deployVerticle(reqPlugin)

      val reqConf = pluginConf(reqPlugin.name)
      val withPlugins = RuleConfWithPlugins(ruleConf, RequestPluginsConf(Nil, Nil, List(reqConf)), ResponsePluginsConf(Nil, Nil, Nil))

      // when
      val result: ValidationNel[InvalidPluginConf, Rule] =
        Await.result(RuleBuilder.build(vertx, JaegerTracing.noTracing, withPlugins), 3 seconds)

      // then
      result match {
        case Success(rule)   => fail(rule.toString)
        case Failure(errors) => errors must be(NonEmptyList(InvalidPluginConf(reqConf, "errorReq")))
      }
    }

    "fail when invalid request plugin and invalid response plugin" in {
      // given
      val reqPlugin = new RequestPlugin() {
        override def validate(c: RequestConfig): ValidateResponse = ValidateError("errorReq")
      }

      val respPlugin = new ResponsePlugin() {
        override def validate(c: ResponseConfig): ValidateResponse = ValidateError("errorResp")
      }

      deployVerticle(reqPlugin)
      deployVerticle(respPlugin)

      val reqConf = pluginConf(reqPlugin.name)
      val respConf = pluginConf(respPlugin.name)
      val withPlugins = RuleConfWithPlugins(ruleConf, RequestPluginsConf(Nil, List(reqConf), Nil), ResponsePluginsConf(Nil, List(respConf), Nil))

      // when
      val result: ValidationNel[InvalidPluginConf, Rule] =
        Await.result(RuleBuilder.build(vertx, JaegerTracing.noTracing, withPlugins), 3 seconds)

      // then
      result match {
        case Success(rule) => fail(rule.toString)
        case Failure(errors) =>
          errors.size must be(2)
          errors.list.toList must contain(InvalidPluginConf(reqConf, "errorReq"))
          errors.list.toList must contain(InvalidPluginConf(respConf, "errorResp"))
      }
    }
  }
}
