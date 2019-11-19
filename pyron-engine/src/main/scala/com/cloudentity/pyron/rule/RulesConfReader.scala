package com.cloudentity.pyron.rule

import io.circe._
import io.circe.parser._
import com.cloudentity.pyron.domain._
import com.cloudentity.pyron.domain.flow.{DiscoverableServiceRule, EndpointMatchCriteria, PathMatching, PathPattern, PathPrefix, PluginConf, PluginName, ProxyServiceRule, RewriteMethod, RewritePath, ServiceClientName, StaticServiceRule, TargetHost, TargetServiceRule}
import com.cloudentity.pyron.domain.http.CallOpts
import com.cloudentity.pyron.domain.rule.{ExtRuleConf, RequestPluginsConf, ResponsePluginsConf, RuleConf, RuleConfWithPlugins}
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.vertx.core.http.HttpMethod
import scalaz.Scalaz._
import scalaz.{ValidationNel, _}

object RulesConfReader {
  import Codecs._

  // list of ServiceRulesConf matches rules.json schema
  case class ServiceRulesConf(default: RuleRawConf, request: Option[ServiceFlowsConf], response: Option[ServiceFlowsConf], endpoints: List[EndpointConf])
  case class ServiceConf(rule: RuleRawConf, request: Option[ServiceFlowsConf], response: Option[ServiceFlowsConf])

  case class ServiceFlowsConf(preFlow: Option[ServiceFlowConf], postFlow: Option[ServiceFlowConf])
  case class ServiceFlowConf(plugins: List[PluginConf])

  case class EndpointConf(rule: RuleRawConf, request: Option[EndpointFlowsConf], response: Option[EndpointFlowsConf])
  case class EndpointFlowsConf(preFlow: Option[EndpointFlowConf], postFlow: Option[EndpointFlowConf])
  case class EndpointFlowConf(disableAllPlugins: Option[Boolean], disablePlugins: Option[List[PluginName]])

  case class RuleRawConf(
    endpointName: Option[String],
    targetHost: Option[TargetHost],
    targetPort: Option[Int],
    targetSsl: Option[Boolean],
    targetService: Option[ServiceClientName],
    targetProxy: Option[Boolean],
    pathPattern: Option[PathPattern],
    rewritePath: Option[RewritePath],
    rewriteMethod: Option[RewriteMethod],
    copyQueryOnRewrite: Option[Boolean],
    preserveHostHeader: Option[Boolean],
    pathPrefix: Option[PathPrefix],
    method: Option[HttpMethod],
    dropPrefix: Option[Boolean],
    requestPlugins: Option[List[PluginConf]],
    responsePlugins: Option[List[PluginConf]],
    tags: Option[List[String]],
    call: Option[CallOpts],
    ext: Option[ExtRuleConf]
  )

  sealed trait ReadRulesError
    case class RuleDecodingError(ex: Throwable) extends ReadRulesError
    case class RuleErrors(errorMsgs: NonEmptyList[String]) extends ReadRulesError

  case class RuleRequiredFields(method: HttpMethod, pathPattern: PathPattern, service: TargetServiceRule)

  object codecs {
    import Codecs._

    implicit lazy val serviceRulesConfEnc: Encoder[ServiceRulesConf] = deriveEncoder
    implicit lazy val serviceRulesConfDec: Decoder[ServiceRulesConf] = deriveDecoder

    implicit lazy val ruleRawConfEnc: Encoder[RuleRawConf] = deriveEncoder
    implicit lazy val ruleRawConfDec: Decoder[RuleRawConf] = deriveDecoder

    implicit lazy val ServiceFlowsConfEnc: Encoder[ServiceFlowsConf] = deriveEncoder
    implicit lazy val ServiceFlowsConfDec: Decoder[ServiceFlowsConf] = deriveDecoder

    implicit lazy val ServiceFlowConfEnc: Encoder[ServiceFlowConf] = deriveEncoder
    implicit lazy val ServiceFlowConfDec: Decoder[ServiceFlowConf] = deriveDecoder

    implicit lazy val EndpointFlowsConfEnc: Encoder[EndpointFlowsConf] = deriveEncoder
    implicit lazy val EndpointFlowsConfDec: Decoder[EndpointFlowsConf] = deriveDecoder

    implicit lazy val EndpointFlowConfEnc: Encoder[EndpointFlowConf] = deriveEncoder
    implicit lazy val EndpointFlowConfDec: Decoder[EndpointFlowConf] = deriveDecoder

    implicit val endpointConfDecoder: Decoder[EndpointConf] = Decoder.decodeJson.emap { json =>
      val result: Either[DecodingFailure, EndpointConf] =
        for {
          rule     <- json.as[RuleRawConf]
          request  <- json.asObject.flatMap(_.toMap.get("request")).map(_.as[Option[EndpointFlowsConf]]).sequenceU
          response <- json.asObject.flatMap(_.toMap.get("response")).map(_.as[Option[EndpointFlowsConf]]).sequenceU
        } yield EndpointConf(rule, request.flatten, response.flatten)

      result.left.map(_.message)
    }

    implicit lazy val endpointConfEncoder: Encoder[EndpointConf] = { conf =>
      conf.rule.asJson.deepMerge(Map("request" -> conf.request, "response" -> conf.response).asJson)
    }
  }

  import codecs._

  def read(json: String): ReadRulesError \/ List[RuleConfWithPlugins] =
    decodeRules(json) match {
      case Right(groupConfs) =>
        composeRuleConfs(groupConfs) match {
          case Success(rules)     => \/-(rules)
          case Failure(errorMsgs) => -\/(RuleErrors(errorMsgs))
        }
      case Left(err)                  => -\/(RuleDecodingError(err))
    }

  def decodeRules(json: String): Either[Throwable, List[ServiceRulesConf]] =
    decode[Map[String, List[ServiceRulesConf]]](json).left.map(wrapDecodingError)
      .map(_.values.flatten.toList).toTry
      .recoverWith { case _ => decode[List[ServiceRulesConf]](json).left.map(wrapDecodingError).toTry }
      .toEither

  private def wrapDecodingError(err: Error): Throwable =
    err match {
      case DecodingFailure(_, ops) => new RuntimeException(s"Invalid JSON at rules${CursorOp.opsToPath(ops)}")
      case ParsingFailure(_, _) => err
    }

  // returns list of Rules on success or non-empty list of missing fields
  def composeRuleConfs(serviceConfs: List[ServiceRulesConf]): ValidationNel[String, List[RuleConfWithPlugins]] = {
    val validations: List[ValidationNel[String, RuleConfWithPlugins]] =
      for {
        (serviceConf, i)  <- serviceConfs.zipWithIndex
        (endpointConf, j) <- serviceConf.endpoints.zipWithIndex
      } yield {
        composeRuleConf(ServiceConf(serviceConf.default, serviceConf.request, serviceConf.response), endpointConf)
          .leftMap(errorMsgs => errorMsgs.map(errorMsg => s"Service $i, endpoint $j,${endpointConf.rule.endpointName.map(n => s" '$n'").getOrElse("")} $errorMsg"))
      }

    validations.sequenceU // IntelliJ has problems with understanding sequenceU - it actually compiles
  }

  def composeRuleConf(defaultConf: ServiceConf, endpointConf: EndpointConf): ValidationNel[String, RuleConfWithPlugins] = {
    val requestPluginsConf = composeRequestPluginsConf(defaultConf, endpointConf)
    val responsePluginConfs = composeResponsePluginsConf(defaultConf, endpointConf)
    val endpointName = endpointConf.rule.endpointName.orElse(defaultConf.rule.endpointName)
    val pathPrefix = endpointConf.rule.pathPrefix.orElse(defaultConf.rule.pathPrefix).getOrElse(PathPrefix(""))
    val dropPrefix = endpointConf.rule.dropPrefix.orElse(defaultConf.rule.dropPrefix).getOrElse(true)
    val copyQueryOnRewrite = endpointConf.rule.copyQueryOnRewrite.orElse(defaultConf.rule.copyQueryOnRewrite)
    val preserveHostHeader = endpointConf.rule.preserveHostHeader.orElse(defaultConf.rule.preserveHostHeader)
    val tags = endpointConf.rule.tags.orElse(defaultConf.rule.tags)
    val call =
      endpointConf.rule.call.map( opts =>
        CallOpts(
          responseTimeout     = readCallOptsAttribute(opts, defaultConf.rule.call, _.responseTimeout),
          retries             = readCallOptsAttribute(opts, defaultConf.rule.call, _.retries),
          failureHttpCodes    = readCallOptsAttribute(opts, defaultConf.rule.call, _.failureHttpCodes),
          retryFailedResponse = readCallOptsAttribute(opts, defaultConf.rule.call, _.retryFailedResponse),
          retryOnException    = readCallOptsAttribute(opts, defaultConf.rule.call, _.retryOnException)
        )
      ).orElse(defaultConf.rule.call)
    val ext = composeExtRuleConf(defaultConf, endpointConf)


    val ruleValidation: ValidationNel[String, RuleConfWithPlugins] =
      composeAndValidateRequiredFields(defaultConf.rule, endpointConf.rule)
        .map { (rf) =>
          val pathMatching = PathMatching.build(pathPrefix, rf.pathPattern)
          val criteria = EndpointMatchCriteria(rf.method, pathMatching)
          val service  = rf.service
          val ruleConf =
            RuleConf(endpointName, criteria, service, dropPrefix, endpointConf.rule.rewriteMethod, endpointConf.rule.rewritePath,
              copyQueryOnRewrite, preserveHostHeader, tags.getOrElse(Nil), call, ext)
          RuleConfWithPlugins(ruleConf, requestPluginsConf, responsePluginConfs)
        }

    ruleValidation
  }

  def composeExtRuleConf(defaultConf: ServiceConf, endpointConf: EndpointConf): ExtRuleConf =
    ExtRuleConf(
      openapi = endpointConf.rule.ext.flatMap(_.openapi).orElse(defaultConf.rule.ext.flatMap(_.openapi))
    )

  def readCallOptsAttribute[A](opts: CallOpts, fallback: Option[CallOpts], read: CallOpts => Option[A]): Option[A] =
    read(opts).orElse(fallback.flatMap(read))

  def composeRequestPluginsConf(defaultConf: ServiceConf, endpointConf: EndpointConf): RequestPluginsConf = {
    val preRequestPluginConfs  = composeFlow(_.request.flatMap(_.preFlow), _.request.flatMap(_.preFlow))(defaultConf, endpointConf)
    val postRequestPluginConfs = composeFlow(_.request.flatMap(_.postFlow), _.request.flatMap(_.postFlow))(defaultConf, endpointConf)
    val requestPluginConfs     = endpointConf.rule.requestPlugins.orElse(defaultConf.rule.requestPlugins).getOrElse(Nil)

    RequestPluginsConf(preRequestPluginConfs, requestPluginConfs, postRequestPluginConfs)
  }

  def composeFlow(f: ServiceConf => Option[ServiceFlowConf], g: EndpointConf => Option[EndpointFlowConf])(sc: ServiceConf, ec: EndpointConf): List[PluginConf] =
    composePluginConfs(f(sc).getOrElse(ServiceFlowConf(Nil)), g(ec).getOrElse(EndpointFlowConf(None, None)))

  def composePluginConfs(serviceFlow: ServiceFlowConf, endpointFlow: EndpointFlowConf): List[PluginConf] =
    if (endpointFlow.disableAllPlugins.getOrElse(false)) Nil
    else serviceFlow.plugins.filter(filterPlugin(endpointFlow.disablePlugins.getOrElse(Nil)))

  def composeResponsePluginsConf(defaultConf: ServiceConf, endpointConf: EndpointConf): ResponsePluginsConf = {
    val preResponsePluginConfs  = composeFlow(_.response.flatMap(_.preFlow), _.response.flatMap(_.preFlow))(defaultConf, endpointConf)
    val postResponsePluginConfs = composeFlow(_.response.flatMap(_.postFlow), _.response.flatMap(_.postFlow))(defaultConf, endpointConf)
    val responsePluginConfs     = endpointConf.rule.responsePlugins.orElse(defaultConf.rule.responsePlugins).getOrElse(Nil)

    ResponsePluginsConf(preResponsePluginConfs, responsePluginConfs, postResponsePluginConfs)
  }

  private def filterPlugin(disabled: List[PluginName])(conf: PluginConf): Boolean =
    !disabled.contains(conf.name)

  /* Some fields in Rule are required. If they are missing in `endpointConf`, the default value is taken from `defaultConf`.
      If the value is missing in both `endpointConf` and `defaultConf` validation error message is emitted.
      All error messages are aggregated and stored in ValidationNel.Failure.
      If there is no errors ValidationNel.Success is returned with Rule.
     */
  def composeAndValidateRequiredFields(defaultConf: RuleRawConf, endpointConf: RuleRawConf): Validation[NonEmptyList[String], RuleRequiredFields] = {
    orElse(endpointConf.method, defaultConf.method, "missing `method`") |@|
    orElse(endpointConf.pathPattern, defaultConf.pathPattern, "missing `pathPattern`") |@|
    validateTargetServiceRule(
      endpointConf.targetHost.orElse(defaultConf.targetHost),
      endpointConf.targetPort.orElse(defaultConf.targetPort),
      endpointConf.targetSsl.orElse(defaultConf.targetSsl),
      endpointConf.targetService.orElse(defaultConf.targetService),
      endpointConf.targetProxy.orElse(defaultConf.targetProxy)
    )
  }.apply(RuleRequiredFields)

  private def validateTargetServiceRule(hostOpt: Option[TargetHost], portOpt: Option[Int], sslOpt: Option[Boolean], serviceNameOpt: Option[ServiceClientName], targetProxyOpt: Option[Boolean]): ValidationNel[String, TargetServiceRule] = {
    val discoverableServiceOpt = serviceNameOpt.map(DiscoverableServiceRule)
    val staticServiceOpt: Option[StaticServiceRule] =
      for {
        host <- hostOpt
        port <- portOpt
      } yield (StaticServiceRule(host, port, sslOpt.getOrElse(false)))
    val proxyServiceOpt = if (targetProxyOpt.getOrElse(false)) Some(ProxyServiceRule) else None

    List(staticServiceOpt, discoverableServiceOpt, proxyServiceOpt).flatten match {
      case service :: Nil => Success(service)
      case _ => Validation.failureNel("either 'targetHost' and 'targetPort' or 'targetService' or 'targetProxy' should be set")
    }
  }

  private def orElse[A](a: Option[A], b: Option[A], errMsg: String): ValidationNel[String, A] =
    a.orElse(b).toSuccessNel(errMsg)
}