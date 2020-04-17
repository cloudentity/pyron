package com.cloudentity.pyron.rule

import io.circe.Json
import com.cloudentity.pyron.domain._
import com.cloudentity.pyron.domain.flow.{EndpointMatchCriteria, PathMatching, PathPattern, PathPrefix, PluginConf, ApiGroupPluginConf, PluginName, ServiceClientName, StaticServiceRule, TargetHost}
import com.cloudentity.pyron.domain.rule.{ExtRuleConf, RequestPluginsConf, ResponsePluginsConf, RuleConf}
import com.cloudentity.pyron.rule.RulesConfReader._
import io.vertx.core.http.HttpMethod
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}
import scalaz.{Failure, Success}

@RunWith(classOf[JUnitRunner])
class RulesConfReaderSpec extends WordSpec with MustMatchers {
  val emptyRuleConf = RulesConfReader.emptyRuleRawConf
  val emptyConf = ServiceConf(emptyRuleConf, None, None)
  val fullRuleConfWoPlugins = RuleRawConf(Some("endpointName"), Some(TargetHost("targetHost")), Some(80), None, None, None, Some(PathPattern("pathPattern")), None, None, None, None, Some(PathPrefix("pathPrefix")), Some(HttpMethod.GET), Some(true), None, None, None, None, None, None, None)
  val otherFullRuleConfWoPlugins = RuleRawConf(Some("endpointName2"), Some(TargetHost("targetHost2")), Some(802), None, None, None, Some(PathPattern("pathPattern2")), None, None, None, None, Some(PathPrefix("pathPrefix2")), Some(HttpMethod.POST), Some(false), None, None, None, None, None, None, None)

  def serviceConf(rule: RuleRawConf) = ServiceConf(rule, None, None)
  def endpointConf(rule: RuleRawConf) = EndpointConf(rule, None, None)

  "RulesConfReader.composeRuleConf" should {
    val composeRuleConf = RulesConfReader.composeRuleConf _

    val targetError = "either 'targetHost' and 'targetPort' or 'targetService' or 'targetProxy' should be set"
    "return error when `targetHost` and `targetPort` and `targetService` and 'targetProxy' missing in default and endpoint conf" in {
      composeRuleConf(Map(), emptyConf, endpointConf(fullRuleConfWoPlugins.copy(targetHost = None, targetPort = None, targetService = None, targetProxy = None))) match {
        case Success(_)  => fail
        case Failure(fs) => fs.list.toList must be(List(targetError))
      }
    }

    "return error when `targetHost` or `targetPort` and `targetService` missing in default and endpoint conf" in {
      composeRuleConf(Map(), emptyConf, endpointConf(fullRuleConfWoPlugins.copy(targetPort = None, targetService = None))) match {
        case Success(_)  => fail
        case Failure(fs) => fs.list.toList must be(List(targetError))
      }

      composeRuleConf(Map(), emptyConf, endpointConf(fullRuleConfWoPlugins.copy(targetHost = None, targetService = None))) match {
        case Success(_)  => fail
        case Failure(fs) => fs.list.toList must be(List(targetError))
      }
    }

    "return error when `targetHost` and `targetPort` and `targetService` present in conf" in {
      composeRuleConf(Map(), emptyConf, endpointConf(fullRuleConfWoPlugins.copy(targetService = Some(ServiceClientName("service-x"))))) match {
        case Success(_)  => fail
        case Failure(fs) => fs.list.toList must be(List(targetError))
      }
    }

    "return error when `targetHost` and `targetPort` and `targetProxy` present in conf" in {
      composeRuleConf(Map(), emptyConf, endpointConf(fullRuleConfWoPlugins.copy(targetProxy = Some(true)))) match {
        case Success(_)  => fail
        case Failure(fs) => fs.list.toList must be(List(targetError))
      }
    }

    "return error when `targetService` and `targetProxy` present in conf" in {
      composeRuleConf(Map(), emptyConf, endpointConf(fullRuleConfWoPlugins.copy(targetService = Some(ServiceClientName("service-x")), targetProxy = Some(true)))) match {
        case Success(_)  => fail
        case Failure(fs) => fs.list.toList must be(List(targetError))
      }
    }

    "return error when `pathPattern` missing in default and endpoint conf" in {
      composeRuleConf(Map(), emptyConf, endpointConf(fullRuleConfWoPlugins.copy(pathPattern = None))) match {
        case Success(_)  => fail
        case Failure(fs) => fs.list.toList must be(List("missing `pathPattern`"))
      }
    }

    val fullConfRule = Rule(
      conf =
        RuleConf(
          endpointName    = fullRuleConfWoPlugins.endpointName,
          criteria        = EndpointMatchCriteria(fullRuleConfWoPlugins.method.get, PathMatching((s"^${fullRuleConfWoPlugins.pathPrefix.get.value}${fullRuleConfWoPlugins.pathPattern.get.value}$$").r, Nil, fullRuleConfWoPlugins.pathPrefix.get, "")),
          target         = StaticServiceRule(fullRuleConfWoPlugins.targetHost.get, fullRuleConfWoPlugins.targetPort.get, fullRuleConfWoPlugins.targetSsl.getOrElse(false)),
          dropPathPrefix  = fullRuleConfWoPlugins.dropPrefix.get,
          rewritePath     = fullRuleConfWoPlugins.rewritePath,
          rewriteMethod   = fullRuleConfWoPlugins.rewriteMethod,
          copyQueryOnRewrite = fullRuleConfWoPlugins.copyQueryOnRewrite,
          preserveHostHeader = fullRuleConfWoPlugins.preserveHostHeader,
          tags               = fullRuleConfWoPlugins.tags.getOrElse(Nil),
          requestBody        = fullRuleConfWoPlugins.requestBody,
          requestBodyMaxSize = fullRuleConfWoPlugins.requestBodyMaxSize,
          call               = fullRuleConfWoPlugins.call,
          ext                = ExtRuleConf(None)
        ),
      requestPlugins  = Nil,
      responsePlugins = Nil
    )

    "return RuleConf with all values copied from default conf when endpoint conf empty" in {
      composeRuleConf(Map(), serviceConf(fullRuleConfWoPlugins), endpointConf(emptyRuleConf)) match {
        case Success(rule) => assertRulesEqual(rule.rule, fullConfRule.conf)
        case Failure(fs)   => fail(fs.toString)
      }
    }

    "return RuleConf with all values from endpoint conf when endpoint conf without missing values" in {
      composeRuleConf(Map(), serviceConf(otherFullRuleConfWoPlugins), endpointConf(fullRuleConfWoPlugins)) match {
        case Success(rule) => assertRulesEqual(rule.rule, fullConfRule.conf)
        case Failure(fs)   => fail(fs.toString)
      }
    }
  }

  def assertRulesEqual(r1: RuleConf, r2: RuleConf): Unit = {
    // Regex has broken equals method (because of broken java.util.regex.Pattern.equals; it checks reference equality),
    // so we assert `regex: String` value and then copy the criteria.path
    r1.criteria.path.regex.regex must be(r2.criteria.path.regex.regex)
    r1 must be(r2.copy(criteria = r2.criteria.copy(path = r1.criteria.path)))
  }

  "RulesReader.composeRuleConfs" should {
    "should aggregate all missing fields" in {
      val conf = ServiceRulesConf(Some(emptyRuleConf), None, None, List(endpointConf(emptyRuleConf)))
      RulesConfReader.composeRuleConfs(List(conf, conf), Map()) match {
        case Success(_) => fail
        case Failure(fs) =>
          val service0MissingFields = fs.list.toList.filter(_.startsWith("Service 0"))
          val service1MissingFields = fs.list.toList.filter(_.startsWith("Service 1"))

          service0MissingFields.size must be(service1MissingFields.size)
      }
    }
  }

  val preFlowRequestPluginConfs = List(ApiGroupPluginConf(PluginName("pre-req-plugin1"), Json.Null, None), ApiGroupPluginConf(PluginName("pre-req-plugin2"), Json.Null, None))
  val postFlowRequestPluginConfs = List(ApiGroupPluginConf(PluginName("post-req-plugin1"), Json.Null, None), ApiGroupPluginConf(PluginName("post-req-plugin2"), Json.Null, None))
  val preFlowResponsePluginConfs = List(ApiGroupPluginConf(PluginName("pre-resp-plugin1"), Json.Null, None), ApiGroupPluginConf(PluginName("pre-resp-plugin2"), Json.Null, None))
  val postFlowResponsePluginConfs = List(ApiGroupPluginConf(PluginName("post-resp-plugin1"), Json.Null, None), ApiGroupPluginConf(PluginName("post-resp-plugin2"), Json.Null, None))

  val serviceConf  = ServiceConf(emptyRuleConf,
    request  = Some(ServiceFlowsConf(preFlow = Some(ServiceFlowConf(preFlowRequestPluginConfs.map(p => PluginConf(p.name, p.conf)))),  postFlow = Some(ServiceFlowConf(postFlowRequestPluginConfs.map(p => PluginConf(p.name, p.conf)))))),
    response = Some(ServiceFlowsConf(preFlow = Some(ServiceFlowConf(preFlowResponsePluginConfs.map(p => PluginConf(p.name, p.conf)))), postFlow = Some(ServiceFlowConf(postFlowResponsePluginConfs.map(p => PluginConf(p.name, p.conf))))))
  )

  "RulesReader.composeRequestPluginsConf" should {
    val composeRequestPluginsConf = RulesConfReader.composeRequestPluginsConf _
    "set plugins" in {
      // given
      val endpointConf = EndpointConf(emptyRuleConf, None, None)

      // when
      val conf = composeRequestPluginsConf(Map(), serviceConf, endpointConf)

      // then
      conf must be(RequestPluginsConf(preFlowRequestPluginConfs, Nil, postFlowRequestPluginConfs))
    }

    "filter-out all preFlow plugins" in {
      // given
      val endpointRequestFlows = Some(EndpointFlowsConf(preFlow = Some(EndpointFlowConf(disableAllPlugins = Some(true), None)), postFlow = None))
      val endpointConf         = EndpointConf(emptyRuleConf, endpointRequestFlows, None)

      // when
      val conf = composeRequestPluginsConf(Map(), serviceConf, endpointConf)

      // then
      conf must be(RequestPluginsConf(Nil, Nil, postFlowRequestPluginConfs))
    }

    "filter-out all postFlow plugins" in {
      // given
      val endpointRequestFlows = Some(EndpointFlowsConf(postFlow = Some(EndpointFlowConf(disableAllPlugins = Some(true), None)), preFlow = None))
      val endpointConf  = EndpointConf(emptyRuleConf, endpointRequestFlows, None)

      // when
      val conf = composeRequestPluginsConf(Map(), serviceConf, endpointConf)

      // then
      conf must be(RequestPluginsConf(preFlowRequestPluginConfs, Nil, Nil))
    }

    "filter-out some plugins" in {
      // given
      val endpointRequestFlows = Some(
        EndpointFlowsConf(
          preFlow  = Some(EndpointFlowConf(disableAllPlugins = None, disablePlugins = Some(List(PluginName("pre-req-plugin1"))))),
          postFlow = Some(EndpointFlowConf(disableAllPlugins = None, disablePlugins = Some(List(PluginName("post-req-plugin1")))))
        )
      )
      val endpointResponseFlows = Some(EndpointFlowsConf(preFlow = None, postFlow = None))
      val endpointConf          = EndpointConf(emptyRuleConf, endpointRequestFlows, endpointResponseFlows)

      // when
      val conf = composeRequestPluginsConf(Map(), serviceConf, endpointConf)

      // then
      conf must be(
        RequestPluginsConf(
          preFlowRequestPluginConfs.filterNot(_.name == PluginName("pre-req-plugin1")), 
          Nil,
          postFlowRequestPluginConfs.filterNot(_.name == PluginName("post-req-plugin1"))
        )
      )
    }
  }

  "RulesReader.composeResponsePluginsConf" should {
    val composeResponsePluginsConf = RulesConfReader.composeResponsePluginsConf _
    "set plugins" in {
      // given
      val endpointConf = EndpointConf(emptyRuleConf, None, None)

      // when
      val conf = composeResponsePluginsConf(Map(), serviceConf, endpointConf)

      // then
      conf must be(ResponsePluginsConf(preFlowResponsePluginConfs, Nil, postFlowResponsePluginConfs))
    }

    "filter-out all preFlow plugins" in {
      // given
      val endpointResponseFlows = Some(EndpointFlowsConf(preFlow = Some(EndpointFlowConf(disableAllPlugins = Some(true), None)), postFlow = None))
      val endpointConf          = EndpointConf(emptyRuleConf, None, endpointResponseFlows)

      // when
      val conf = composeResponsePluginsConf(Map(), serviceConf, endpointConf)

      // then
      conf must be(ResponsePluginsConf(Nil, Nil, postFlowResponsePluginConfs))
    }

    "filter-out all postFlow plugins" in {
      // given
      val endpointResponseFlows = Some(EndpointFlowsConf(postFlow = Some(EndpointFlowConf(disableAllPlugins = Some(true), None)), preFlow = None))
      val endpointConf          = EndpointConf(emptyRuleConf, None, endpointResponseFlows)

      // when
      val conf = composeResponsePluginsConf(Map(), serviceConf, endpointConf)

      // then
      conf must be(ResponsePluginsConf(preFlowResponsePluginConfs, Nil, Nil))
    }

    "filter-out some plugins" in {
      // given
      val endpointRequestFlows  = Some(EndpointFlowsConf(preFlow = None, postFlow = None))
      val endpointResponseFlows = Some(
        EndpointFlowsConf(
          preFlow = Some(EndpointFlowConf(disableAllPlugins = None, disablePlugins = Some(List(PluginName("pre-resp-plugin1"))))),
          postFlow = Some(EndpointFlowConf(disableAllPlugins = None, disablePlugins = Some(List(PluginName("post-resp-plugin1")))))
        )
      )
      val endpointConf     = EndpointConf(emptyRuleConf, endpointRequestFlows, endpointResponseFlows)

      // when
      val conf = composeResponsePluginsConf(Map(), serviceConf, endpointConf)

      // then
      conf must be(
        ResponsePluginsConf(
          preFlowResponsePluginConfs.filterNot(_.name == PluginName("pre-resp-plugin1")),
          Nil,
          postFlowResponsePluginConfs.filterNot(_.name == PluginName("post-resp-plugin1"))
        )
      )
    }
  }
}
