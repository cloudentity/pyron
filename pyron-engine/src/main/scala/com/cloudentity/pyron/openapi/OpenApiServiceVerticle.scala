package com.cloudentity.pyron.openapi

import com.cloudentity.pyron.apigroup.{ApiGroup, ApiGroupConf, ApiGroupsChangeListener, ApiGroupsStore}
import com.cloudentity.pyron.client.TargetClient
import com.cloudentity.pyron.domain.flow._
import com.cloudentity.pyron.domain.http.{RelativeUri, TargetRequest}
import com.cloudentity.pyron.domain.openapi._
import com.cloudentity.pyron.domain.rule.RuleConfWithPlugins
import com.cloudentity.pyron.openapi.Codecs._
import com.cloudentity.pyron.openapi.OpenApiService._
import com.cloudentity.pyron.util.ConfigDecoder
import com.cloudentity.tools.vertx.http.Headers
import com.cloudentity.tools.vertx.scala.Operation
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.swagger.models.Swagger
import io.swagger.parser.SwaggerParser
import io.vertx.core.http.HttpMethod

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class OpenApiServiceVerticle extends ScalaServiceVerticle with OpenApiService with ConfigDecoder with ApiGroupsChangeListener {

  var targetClient: TargetClient = _
  var apiGroupConfs: List[ApiGroupConf] = List()

  lazy val converter: OpenApiConverter = createClient(classOf[OpenApiConverter])

  override def configPath(): String = "openApi"

  var cfg: OpenApiConf = _

  override def initServiceAsyncS(): Future[Unit] = {
    cfg = Option(getConfig).flatMap(json => decodeConfigUnsafe[Option[OpenApiConf]]).getOrElse(OpenApiConf(None, None, None))

    lazy val apiGroupsStore = createClient(classOf[ApiGroupsStore])
    for {
      confs     <- apiGroupsStore.getGroupConfs().toScala()
      rules     <- apiGroupsStore.getGroups().toScala()
      _         <- resetRulesAndTargetClient(rules, confs)
    } yield ()
  }

  override def apiGroupsChanged(groups: List[ApiGroup], confs: List[ApiGroupConf]): Unit =
    resetRulesAndTargetClient(groups, confs)

  private def getSourceConf(serviceId: ServiceId) =
    cfg.services.getOrElse(Map()).get(serviceId).flatMap(_.source)
      .orElse(cfg.defaultSource)
      .getOrElse(SourceConf(RelativeUri.fromPath("/docs/index-resolved.yaml")))

  private def getConverterConf(serviceId: ServiceId) =
    cfg.services.getOrElse(Map()).get(serviceId).flatMap(_.converter)
      .orElse(cfg.defaultConverter)
      .getOrElse(ConverterConf(None, None))

  def resetRulesAndTargetClient(groups: List[ApiGroup], confs: List[ApiGroupConf]): Future[Unit] =
    TargetClient.resetTargetClient(vertx, getConfService, getTracing, groups.flatMap(_.rules).map(_.conf), Option(targetClient))
      .map { client =>
        apiGroupConfs = confs
        targetClient  = client
      }

  override def build(ctx: TracingContext, serviceId: ServiceId, tag: Option[String]): VxFuture[OpenApiServiceError \/ Swagger] = {
    val openApiRules = for {
      group <- apiGroupConfs
      ruleConfWithPlugins <- group.rules
      openApiRule <- OpenApiRuleBuilder.from(ruleConfWithPlugins, group.matchCriteria)
    } yield openApiRule

    val filteredRules = filterOpenApiRules(openApiRules, serviceId, tag)

    val program: Operation[OpenApiServiceError, Swagger] = for {
      _             <- filteredRules.headOption.toOperation[OpenApiServiceError](NoRulesFound)
      sourceConf    = getSourceConf(serviceId)
      openApi       <- fetchOpenApi(ctx, serviceId, sourceConf)
      converterConf = getConverterConf(serviceId)
      apiGwOpenApi  <- converter.convert(ctx, serviceId, openApi, filteredRules, converterConf).toOperation[OpenApiServiceError]
    } yield apiGwOpenApi

    program.run.toJava()
  }

  private def filterOpenApiRules(rules: List[OpenApiRule], serviceId: ServiceId, tag: Option[String]): List[OpenApiRule] =
    rules.filter(_.serviceId == serviceId).filter(r => tag.forall(r.tags.contains(_)))

  def fetchOpenApi(tracing: TracingContext, serviceId: ServiceId, sourceConf: SourceConf): Operation[OpenApiServiceError, Swagger] = {
    val service = serviceId match {
      case DiscoverableServiceId(name) => DiscoverableService(ServiceClientName(name.value))
      case StaticServiceId(host, port, ssl) => StaticService(host, port, ssl)
    }

    val targetRequest = TargetRequest(HttpMethod.GET, service, sourceConf.path, Headers(), None)

    targetClient.call(tracing, targetRequest, None, None).toOperation
      .leftMap[OpenApiServiceError](ex => ClientError(ex))
      .flatMap { resp =>
        resp.http.statusCode() match {
          case 200 => Try(Option(new SwaggerParser().parse(resp.body.toString()))) match {
            case Success(Some(swagger)) => Operation.success(swagger)
            case Success(None) => Operation.error(EmptyOpenApi)
            case Failure(ex) => Operation.error(OpenApiParsingError(ex))
          }
          case 404 => Operation.error(OpenApiNotFound)
          case statusCode => Operation.error(InvalidStatusCode(statusCode))
        }
      }
  }

}

object OpenApiRuleBuilder {
  def from(r: RuleConfWithPlugins, groupMatchCriteria: GroupMatchCriteria): Option[OpenApiRule] = {
    val serviceIdOpt =
      r.rule.target match {
        case DiscoverableServiceRule(name)      => Some(DiscoverableServiceId(name))
        case StaticServiceRule(host, port, ssl) => Some(StaticServiceId(host, port, ssl))
        case _                                  => None
      }

    serviceIdOpt.map(serviceId =>
      OpenApiRule(
        method         = r.rule.criteria.method,
        serviceId      = serviceId,
        group          = groupMatchCriteria,
        pathPrefix     = PathPrefix(r.rule.criteria.rewrite.pathPrefix),
        pathPattern    = PathPattern(r.rule.criteria.rewrite.matchPattern),
        dropPathPrefix = r.rule.dropPathPrefix,
        reroute        = r.rule.reroute,
        rewritePath    = r.rule.rewritePath,
        rewriteMethod  = r.rule.rewriteMethod,
        plugins        = r.requestPlugins.toList ::: r.responsePlugins.toList,
        tags           = r.rule.tags,
        operationId    = r.rule.ext.openapi.flatMap(_.operationId)
      )
    )
  }
}