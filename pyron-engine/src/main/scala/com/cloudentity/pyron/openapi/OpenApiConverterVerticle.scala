package com.cloudentity.pyron.openapi

import com.cloudentity.pyron.domain.flow.ApiGroupPluginConf
import com.cloudentity.pyron.domain.openapi.{ConverterConf, OpenApiRule, ServiceId}
import com.cloudentity.pyron.plugin.ConvertOpenApiService
import com.cloudentity.pyron.plugin.openapi._
import com.cloudentity.tools.vertx.registry.{RegistryVerticle, ServiceClientsFactory, ServiceClientsRepository}
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.swagger.models._
import io.vertx.core.logging.{Logger, LoggerFactory}
import io.vertx.core.{Future => VxFuture}

import java.util.Optional
import scala.collection.JavaConverters._
import scala.concurrent.Future

case class Api(path: String, method: HttpMethod, operation: Operation)

class OpenApiConverterVerticle extends ScalaServiceVerticle with OpenApiConverter with OpenApiConverterUtils {
  val log: Logger = LoggerFactory.getLogger(this.getClass)

  var preProcessors: ServiceClientsRepository[OpenApiPreProcessor] = _
  var postProcessors: ServiceClientsRepository[OpenApiPostProcessor] = _

  override def initServiceAsyncS(): Future[Unit] = {
    def registryClient[A](typ: String, clazz: Class[A]): Future[ServiceClientsRepository[A]] =
      ServiceClientsFactory.build(vertx.eventBus(), typ, clazz).toScala

    for {
      _    <- RegistryVerticle.deploy(vertx, "openApiPreProcessors", isConfRequired = false).toScala
      _    <- RegistryVerticle.deploy(vertx, "openApiPostProcessors", isConfRequired = false).toScala
      pre  <- registryClient("openApiPreProcessors", classOf[OpenApiPreProcessor])
      post <- registryClient("openApiPostProcessors", classOf[OpenApiPostProcessor])
    } yield {
      preProcessors  = pre
      postProcessors = post
    }
  }

  override def convert(ctx: TracingContext, serviceId: ServiceId, swagger: Swagger, rules: List[OpenApiRule],
                       conf: ConverterConf): VxFuture[Swagger] = {
    preProcess(swagger, conf)
      .compose(swagger => filterExposedApis(ctx, swagger, rules))
      .compose(swagger => rewritePathsAndMethodsAndOperationId(ctx, swagger, rules))
      .compose(swagger => applyPlugins(ctx, swagger, rules))
      .compose(swagger => resolveOperationIdConflicts(ctx, swagger))
      .compose(swagger => modifyMetadata(ctx, swagger, conf))
      .compose(swagger => postProcess(swagger, conf))
  }

  def preProcess(swagger: Swagger, conf: ConverterConf): VxFuture[Swagger] =
    conf.processors.flatMap(_.pre).getOrElse(Nil).flatMap(name => Option(preProcessors.get(name)))
      .foldLeft(VxFuture.succeededFuture(swagger)) { case (acc, processor) =>
        acc.compose(processor.preProcess _)
      }

  def postProcess(swagger: Swagger, conf: ConverterConf): VxFuture[Swagger] =
    conf.processors.flatMap(_.post).getOrElse(Nil).flatMap(name => Option(postProcessors.get(name)))
      .foldLeft(VxFuture.succeededFuture(swagger)) { case (acc, processor) =>
        acc.compose(processor.postProcess _)
      }

  def rewritePathsAndMethodsAndOperationId(ctx: TracingContext, swagger: Swagger, rules: List[OpenApiRule]): VxFuture[Swagger] = {
    val paths: Map[String, Map[HttpMethod, Operation]] = rules.flatMap { rule =>
      val targetServicePath = rule.targetServicePath
      val apiGwPath = rule.apiGwPath
      val targetMethod = toSwaggerMethod(rule.rewriteMethod.map(_.value).getOrElse(rule.method))
      val apiGwMethod = toSwaggerMethod(rule.method)
      findOperation(swagger, targetServicePath, targetMethod) match {
        case Some(operation) =>
          val withHardcodedParamsOperation = adjustPathParams(operation, swagger, targetServicePath)
          val outOperation = deepCopyOperation(withHardcodedParamsOperation)

          val operationId = rule.operationId.getOrElse(operation.getOperationId)
          outOperation.setOperationId(operationId)

          Some((apiGwPath, (apiGwMethod, outOperation)))
        case None =>
          log.warn(ctx, s"Can't find rule definition: $rule in target service docs")
          None
      }

    }.groupBy(t => t._1).mapValues(_.map(_._2).toMap)

    VxFuture.succeededFuture(swagger.paths(buildPaths(paths).asJava))
  }

  def modifyMetadata(ctx: TracingContext, swagger: Swagger, conf: ConverterConf): VxFuture[Swagger] = {
    val basePath = conf.defaults.flatMap(_.basePath).map(_.value).getOrElse("/api")
    val host = conf.defaults.flatMap(_.host).map(_.value).getOrElse("localhost")
    val scheme = if (conf.defaults.flatMap(_.ssl).getOrElse(true)) Scheme.HTTPS else Scheme.HTTP

    VxFuture.succeededFuture(swagger.basePath(basePath).host(host).schemes(List(scheme).asJava))
  }

  def resolveOperationIdConflicts(ctx: TracingContext, swagger: Swagger): VxFuture[Swagger] = {
    def findConflicts(swagger: Swagger): List[Api] =
      swagger.getPaths.asScala.flatMap {
        case (key, value) => value.getOperationMap.asScala.map {
          case (method, operation) => Api(buildAbsolutePath(swagger, key), method, operation)
        }
      }.groupBy(_.operation.getOperationId).filter(_._2.size > 1).values.flatten.toList

    def renameOperationId(operation: Operation, api: Api): Unit = {
      val camelCasePath =
        api.path.split('/')
          .map(segment => if (segment.startsWith("{") && segment.endsWith("}")) "with" + segment.drop(1).dropRight(1).capitalize else segment)
          .map(_.capitalize)
          .mkString("")
      val newOperationId = api.method.toString.toLowerCase + camelCasePath
      operation.setOperationId(newOperationId)
    }

    findConflicts(swagger).foreach { api =>
      findOperation(swagger, api.path, api.method).foreach { operation =>
        renameOperationId(operation, api) // mutable!
      }
    }

    VxFuture.succeededFuture(swagger)
  }

  def applyPlugins(ctx: TracingContext, swagger: Swagger, rules: List[OpenApiRule]): VxFuture[Swagger] = {
    val applyList = rules.flatMap(rule => rule.plugins.map(plugin => (rule, plugin)))
    applyList.foldLeft(VxFuture.succeededFuture(swagger)) { case (acc, (rule, plugin)) =>
      acc.compose { sw => applyPlugin(ctx, plugin, rule, sw) }
    }
  }

  def applyPlugin(ctx: TracingContext, pluginConf: ApiGroupPluginConf, rule: OpenApiRule, swagger: Swagger): VxFuture[Swagger] = {
    val client = getOpenApiPluginClient(pluginConf)
    client.convertOpenApi(ctx, ConvertOpenApiRequest(swagger, rule, pluginConf))
      .compose {
        case ConvertedOpenApi(s) => VxFuture.succeededFuture(s)
        case ConvertOpenApiFailure(msg) => VxFuture.failedFuture(msg)
        case ConvertOpenApiError(msg) => VxFuture.failedFuture(msg)
      }
  }

  def getOpenApiPluginClient(plugin: ApiGroupPluginConf): ConvertOpenApiService = {
    createClient(classOf[ConvertOpenApiService], Optional.of(plugin.addressPrefixOpt.map(_.value).getOrElse(plugin.name.value)))
  }

  def filterExposedApis(ctx: TracingContext, swagger: Swagger, rules: List[OpenApiRule]): VxFuture[Swagger] = {
    val targetServiceOperations = buildAbsolutePathsMap(swagger)

    val exposedApiPaths = targetServiceOperations.flatMap {
      case (path, operations) => findExposedPath(rules, path, operations).map(x => (path, x))
    }

    val filteredOperations = swagger.paths(exposedApiPaths.asJava)
    VxFuture.succeededFuture(filteredOperations)
  }

  def findExposedPath(rules: List[OpenApiRule], path: String, operation: Map[HttpMethod, Operation]): Option[Path] = {
    val exposed = operation.filterKeys(method => isExposed(rules, method, path))
    if (exposed.isEmpty) None else Some(buildPath(exposed))
  }

  def isExposed(rules: List[OpenApiRule], targetMethod: HttpMethod, path: String): Boolean =
    rules.exists { rule =>
      val apiGwMethod = rule.rewriteMethod.map(_.value).getOrElse(rule.method)
      val methodMatched = apiGwMethod.toString.toUpperCase == targetMethod.toString.toUpperCase
      methodMatched && OpenApiConverterUtils.pathMatches(rule.targetServicePath, path)
    }

}
