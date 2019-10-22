package com.cloudentity.pyron.openapi

import com.cloudentity.pyron.apigroup.{ApiGroupConf, ApiGroupsChanged, ApiGroupsStore, ApiGroupsStoreVerticle}
import com.cloudentity.pyron.client.TargetClient
import com.cloudentity.pyron.config.Conf
import com.cloudentity.pyron.domain.flow._
import com.cloudentity.pyron.domain.http.{RelativeUri, TargetRequest}
import com.cloudentity.pyron.domain.openapi._
import com.cloudentity.pyron.domain.rule.RuleConfWithPlugins
import com.cloudentity.pyron.openapi.Codecs._
import com.cloudentity.pyron.openapi.OpenApiService._
import com.cloudentity.pyron.rule.RulesConfReader
import com.cloudentity.pyron.util.ConfigDecoder
import com.cloudentity.tools.vertx.bus.VertxBus
import com.cloudentity.tools.vertx.http.Headers
import com.cloudentity.tools.vertx.scala.Operation
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.server.api.errors.ApiError
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.swagger.models.Swagger
import io.swagger.parser.SwaggerParser
import io.vertx.core.http.HttpMethod

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class OpenApiServiceVerticle extends ScalaServiceVerticle with OpenApiService with ConfigDecoder {

  var targetClient: TargetClient = _
  var confs: List[ApiGroupConf] = List()

  lazy val converter = createClient(classOf[OpenApiConverter])

  override def configPath(): String = "openApi"

  var cfg: OpenApiConf = _

  override def initServiceAsyncS(): Future[Unit] = {
    cfg = Option(getConfig).flatMap(json => decodeConfigUnsafe[Option[OpenApiConf]]).getOrElse(OpenApiConf(None, None, None))
    VertxBus.consumePublished(vertx.eventBus(), ApiGroupsStoreVerticle.PUBLISH_API_GROUPS_ADDRESS, classOf[ApiGroupsChanged], resetRulesAndTargetClient)

    lazy val apiGroupsStore = createClient(classOf[ApiGroupsStore])
    for {
      confs     <- apiGroupsStore.getGroupConfs().toScala()
      rules     <- apiGroupsStore.getGroups().toScala()
      _         <- resetRulesAndTargetClient(ApiGroupsChanged(rules, confs))
    } yield ()
  }

  private def getSourceConf(serviceId: ServiceId) =
    cfg.services.getOrElse(Map()).get(serviceId).flatMap(_.source)
      .orElse(cfg.defaultSource)
      .getOrElse(SourceConf(RelativeUri.fromPath("/docs/index-resolved.yaml")))

  private def getConverterConf(serviceId: ServiceId) =
    cfg.services.getOrElse(Map()).get(serviceId).flatMap(_.converter)
      .orElse(cfg.defaultConverter)
      .getOrElse(ConverterConf(None, None))


  def resetRulesAndTargetClient(change: ApiGroupsChanged): Future[Unit] =
    TargetClient.resetTargetClient(vertx, getConfService, getTracing, change.groups.flatMap(_.rules).map(_.conf), Option(targetClient))
      .map { client =>
        confs        = change.confs
        targetClient = client
      }

  override def build(ctx: TracingContext, serviceId: ServiceId, tag: Option[String]): VxFuture[OpenApiServiceError \/ Swagger] = {
    val openApiRules = {
      for {
        group <- confs
        rule  <- group.rules
      } yield OpenApiRuleBuilder.from(rule, group.matchCriteria)
    }.flatten

    val filteredOpenApiRules = filterOpenApiRules(openApiRules, serviceId, tag)

    val program: Operation[OpenApiServiceError, Swagger] = for {
      _                   <- filteredOpenApiRules.headOption.toOperation[OpenApiServiceError](NoRulesFound)
      sourceConf           = getSourceConf(serviceId)
      openApi             <- fetchOpenApi(ctx, serviceId, sourceConf)
      converterConf        = getConverterConf(serviceId)
      apiGatewayOpenApi   <- converter.convert(ctx, serviceId, openApi, filteredOpenApiRules, converterConf).toOperation
    } yield apiGatewayOpenApi

    program.run.toJava()
  }

  private def getRules(): Operation[ApiError, List[RuleConfWithPlugins]] =
    for {
      rulesJson <- getConfService.getGlobalConf().toOperation[ApiError]
      rules     <- RulesConfReader.read(rulesJson.getJsonArray(Conf.rulesConfPath).toString)
        .toOperation.leftMap[ApiError](_ => ApiError.`with`(500, "InvalidRules", "Invalid rules"))
    } yield (rules)

  private def filterOpenApiRules(rules: List[OpenApiRule], serviceId: ServiceId, tag: Option[String]): List[OpenApiRule] =
    rules.filter(_.serviceId == serviceId).filter(r => tag.forall(r.tags.contains(_)))

  def fetchOpenApi(tracing: TracingContext, serviceId: ServiceId, sourceConf: SourceConf): Operation[OpenApiServiceError, Swagger] = {
    val uri = sourceConf.path

    val service = serviceId match {
      case DiscoverableServiceId(name) => DiscoverableService(ServiceClientName(name.value))
      case StaticServiceId(host, port, ssl) => StaticService(host, port, ssl)
    }

    targetClient.call(tracing, TargetRequest(HttpMethod.GET, service, uri, Headers(), None), None)
      .toOperation
      .leftMap[OpenApiServiceError](ex => ClientError(ex))
      .flatMap { resp =>
        if (resp.http.statusCode() == 200) {
          Try(Option(new SwaggerParser().parse(resp.body.toString()))) match {
            case Success(Some(swagger)) => Operation.success(swagger)
            case Success(None)          => Operation.error(EmptyOpenApi)
            case Failure(ex)            => Operation.error(OpenApiParsingError(ex))
          }
        } else if (resp.http.statusCode() == 404){
          Operation.error(OpenApiNotFound)
        } else {
          Operation.error(InvalidStatusCode(resp.http.statusCode))
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
        pathPrefix     = r.rule.criteria.path.prefix,
        pathPattern    = PathPattern(r.rule.criteria.path.originalPath),
        dropPathPrefix = r.rule.dropPathPrefix,
        rewritePath    = r.rule.rewritePath,
        rewriteMethod  = r.rule.rewriteMethod,
        plugins        = r.requestPlugins.toList ::: r.responsePlugins.toList,
        tags           = r.rule.tags
      )
    )
  }
}