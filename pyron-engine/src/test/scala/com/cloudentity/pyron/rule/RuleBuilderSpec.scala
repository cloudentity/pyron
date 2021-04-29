package com.cloudentity.pyron.rule

import java.util.UUID

import io.circe.generic.semiauto._
import io.circe.{Decoder, Json}
import com.cloudentity.pyron.VertxSpec
import com.cloudentity.pyron.plugin.config._
import com.cloudentity.pyron.domain.flow._
import com.cloudentity.pyron.domain.rule._
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
    val _name: String = UUID.randomUUID().toString
    override def name: PluginName = PluginName(_name)
    override def apply(ctx: RequestCtx, conf: RequestConfig): Future[RequestCtx] = throw new NotImplementedError()
    override def validate(c: RequestConfig): ValidateResponse = throw new NotImplementedError()
    override def confDecoder: Decoder[RequestConfig] = deriveDecoder
  }

  case class ResponseConfig(x: String, y: String)
  class ResponsePlugin extends ResponsePluginVerticle[ResponseConfig] {
    val _name: String = UUID.randomUUID().toString
    override def name: PluginName = PluginName(_name)
    override def apply(ctx: ResponseCtx, conf: ResponseConfig): Future[ResponseCtx] = throw new NotImplementedError()
    override def validate(c: ResponseConfig): ValidateResponse = throw new NotImplementedError()
    override def confDecoder: Decoder[ResponseConfig] = deriveDecoder
  }

  def pluginConf(pluginName: PluginName): ApiGroupPluginConf = ApiGroupPluginConf(
    name = pluginName,
    conf = Json.fromFields(List(
      "x" -> Json.fromString("x"),
      "y" -> Json.fromString("y")
    )),
    addressPrefixOpt = None
  )

  "RuleBuilder.build" should {
    val ruleConf = RuleConf(
      endpointName = None,
      criteria = EndpointMatchCriteria(
        HttpMethod.GET,
        PreparedPathRewrite("", "", "")
      ),
      target = StaticServiceRule(TargetHost(""), 9000, ssl = false),
      dropPathPrefix = true,
      rewriteMethod = None,
      rewritePath = None,
      copyQueryOnRewrite = None,
      preserveHostHeader = None,
      tags = Nil,
      requestBody = None,
      requestBodyMaxSize = None,
      call = None,
      ext = ExtRuleConf(None)
    )
    "build Rule without plugins" in {
      // given
      val withPlugins = RuleConfWithPlugins(
        ruleConf,
        RequestPluginsConf(pre = Nil, endpoint = Nil, post = Nil),
        ResponsePluginsConf(pre = Nil, endpoint = Nil, post = Nil)
      )

      // when
      val result: ValidationNel[InvalidPluginConf, Rule] =
        Await.result(RuleBuilder.build(vertx, JaegerTracing.noTracing, withPlugins), 3 seconds)

      // then
      result mustBe Success(Rule(ruleConf, Nil, Nil))
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
        RequestPluginsConf(pre = List(reqConf), endpoint = List(reqConf), post = List(reqConf)),
        ResponsePluginsConf(pre = List(respConf), endpoint = List(respConf), post = List(respConf))
      )

      // when
      val result: ValidationNel[InvalidPluginConf, Rule] =
        Await.result(RuleBuilder.build(vertx, JaegerTracing.noTracing, withPlugins), 3 seconds)

      // then
      result match {
        case Success(rule) =>
          rule.requestPlugins.size mustBe 3
          rule.responsePlugins.size mustBe 6
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
      val withPlugins = RuleConfWithPlugins(
        ruleConf,
        RequestPluginsConf(pre = Nil, endpoint = List(reqConf), post = Nil),
        ResponsePluginsConf(pre = Nil, endpoint = List(respConf), post = Nil)
      )

      // when
      val result: ValidationNel[InvalidPluginConf, Rule] =
        Await.result(RuleBuilder.build(vertx, JaegerTracing.noTracing, withPlugins), 3 seconds)

      // then
      result match {
        case Success(rule)   => fail(rule.toString)
        case Failure(errors) => errors mustBe NonEmptyList(InvalidPluginConf(respConf, "errorResp"))
      }
    }

    "fail when invalid pre response plugin" in {
      // given
      val respPlugin = new ResponsePlugin() {
        override def validate(c: ResponseConfig): ValidateResponse = ValidateError("errorResp")
      }

      deployVerticle(respPlugin)

      val respConf = pluginConf(respPlugin.name)
      val withPlugins = RuleConfWithPlugins(
        ruleConf,
        RequestPluginsConf(pre = Nil, endpoint = Nil, post = Nil),
        ResponsePluginsConf(pre = List(respConf), endpoint = Nil, post = Nil)
      )

      // when
      val result: ValidationNel[InvalidPluginConf, Rule] =
        Await.result(RuleBuilder.build(vertx, JaegerTracing.noTracing, withPlugins), 3 seconds)

      // then
      result match {
        case Success(rule)   => fail(rule.toString)
        case Failure(errors) => errors mustBe NonEmptyList(InvalidPluginConf(respConf, "errorResp"))
      }
    }

    "fail when invalid post response plugin" in {
      // given
      val respPlugin = new ResponsePlugin() {
        override def validate(c: ResponseConfig): ValidateResponse = ValidateError("errorResp")
      }

      deployVerticle(respPlugin)

      val respConf = pluginConf(respPlugin.name)
      val withPlugins = RuleConfWithPlugins(
        ruleConf,
        RequestPluginsConf(pre = Nil, endpoint = Nil, post = Nil),
        ResponsePluginsConf(pre = Nil, endpoint = Nil, post = List(respConf))
      )

      // when
      val result: ValidationNel[InvalidPluginConf, Rule] =
        Await.result(RuleBuilder.build(vertx, JaegerTracing.noTracing, withPlugins), 3 seconds)

      // then
      result match {
        case Success(rule)   => fail(rule.toString)
        case Failure(errors) => errors mustBe NonEmptyList(InvalidPluginConf(respConf, "errorResp"))
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
      val withPlugins = RuleConfWithPlugins(
        ruleConf,
        RequestPluginsConf(pre = Nil, endpoint = List(reqConf), post = Nil),
        ResponsePluginsConf(pre = Nil, endpoint = List(respConf), post = Nil)
      )

      // when
      val result: ValidationNel[InvalidPluginConf, Rule] =
        Await.result(RuleBuilder.build(vertx, JaegerTracing.noTracing, withPlugins), 3 seconds)

      // then
      result match {
        case Success(rule)   => fail(rule.toString)
        case Failure(errors) => errors mustBe NonEmptyList(InvalidPluginConf(reqConf, "errorReq"))
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
      val withPlugins = RuleConfWithPlugins(
        ruleConf,
        RequestPluginsConf(pre = List(reqConf), endpoint = Nil, post = Nil),
        ResponsePluginsConf(pre = Nil, endpoint = Nil, post = Nil)
      )

      // when
      val result: ValidationNel[InvalidPluginConf, Rule] =
        Await.result(RuleBuilder.build(vertx, JaegerTracing.noTracing, withPlugins), 3 seconds)

      // then
      result match {
        case Success(rule)   => fail(rule.toString)
        case Failure(errors) => errors mustBe NonEmptyList(InvalidPluginConf(reqConf, "errorReq"))
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
      val withPlugins = RuleConfWithPlugins(
        ruleConf,
        RequestPluginsConf(pre = Nil, endpoint = Nil, post = List(reqConf)),
        ResponsePluginsConf(pre = Nil, endpoint = Nil, post = Nil)
      )

      // when
      val result: ValidationNel[InvalidPluginConf, Rule] =
        Await.result(RuleBuilder.build(vertx, JaegerTracing.noTracing, withPlugins), 3 seconds)

      // then
      result match {
        case Success(rule)   => fail(rule.toString)
        case Failure(errors) => errors mustBe NonEmptyList(InvalidPluginConf(reqConf, "errorReq"))
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
      val withPlugins = RuleConfWithPlugins(
        ruleConf,
        RequestPluginsConf(pre = Nil, endpoint = List(reqConf), post = Nil),
        ResponsePluginsConf(pre = Nil, endpoint = List(respConf), post = Nil)
      )

      // when
      val result: ValidationNel[InvalidPluginConf, Rule] =
        Await.result(RuleBuilder.build(vertx, JaegerTracing.noTracing, withPlugins), 3 seconds)

      // then
      result match {
        case Success(rule) => fail(rule.toString)
        case Failure(errors) =>
          errors.size mustBe 2
          errors.list.toList must contain(InvalidPluginConf(reqConf, "errorReq"))
          errors.list.toList must contain(InvalidPluginConf(respConf, "errorResp"))
      }
    }
  }
}
