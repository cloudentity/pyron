package com.cloudentity.edge.plugin.impl.authn

import io.circe.{Decoder, Json}
import com.cloudentity.edge.api.Responses.Errors
import com.cloudentity.edge.plugin.openapi._
import com.cloudentity.edge.plugin.config._
import com.cloudentity.edge.domain.authn.CloudentityAuthnCtx.{apply => _, unapply => _, _}
import com.cloudentity.edge.domain.flow._
import com.cloudentity.edge.domain.http.ApiResponse
import com.cloudentity.edge.domain.openapi.OpenApiRule
import com.cloudentity.edge.plugin.RequestPluginService
import com.cloudentity.edge.plugin.impl.authn.AuthnPlugin._
import com.cloudentity.edge.plugin.impl.authn.openapi.AuthnPluginOpenApiConverter
import com.cloudentity.edge.plugin.verticle.{PluginOpenApiConverter, RequestPluginVerticle}
import com.cloudentity.tools.vertx.registry.RegistryVerticle
import com.cloudentity.tools.vertx.scala.FutureConversions
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.swagger.models.Swagger
import com.cloudentity.tools.vertx.scala.VertxExecutionContext
import scalaz.EitherT._
import scalaz.Scalaz._
import scalaz.{-\/, \/, \/-}

import scala.concurrent.Future

object AuthnPlugin {
  type AuthnMethodName = String
  type AuthnEntityType = String

  case class Authenticated(method: AuthnMethodName, ctx: AuthnCtx, modify: Modify)

  object Modify {
    def noop = Modify(identity, identity)
  }

  case class Modify(ifAuthnSucceeded: RequestCtx => RequestCtx, ifAuthnFailed: ApiResponse => ApiResponse) {
    def compose(m: Modify) = Modify(ifAuthnSucceeded compose m.ifAuthnSucceeded, ifAuthnFailed compose m.ifAuthnFailed)
  }

  sealed trait AuthnProviderResult
    case class AuthnSuccess(ctx: AuthnCtx, modify: Modify) extends AuthnProviderResult
    case class AuthnFailure(resp: ApiResponse, modify: Modify) extends AuthnProviderResult

  object AuthnFailure {
    def apply(resp: ApiResponse): AuthnFailure = AuthnFailure(resp, Modify.noop)
  }

  object AuthnSuccess {
    def apply(ctx: AuthnCtx): AuthnSuccess = AuthnSuccess(ctx, Modify.noop)
  }

  sealed trait AuthnPluginError
    case object NoMatchingAuthnMethod extends AuthnPluginError
    case class AuthnFailResponse(resp: ApiResponse, modify: Modify) extends AuthnPluginError
}

class AuthnPlugin() extends RequestPluginVerticle[AuthnPluginConf] with PluginOpenApiConverter[AuthnPluginConf]{
  override def name: PluginName = PluginName("authn")
  override def confDecoder: Decoder[AuthnPluginConf] = codecs.AuthnPluginConfDec

  var authnProviders: Map[AuthnMethodName, AuthnProvider] = _
  var authnEntityProviders: Map[AuthnMethodName, Map[AuthnEntityType, EntityProvider]] = _
  var worker: AuthnPluginWorker = _
  var cfg: AuthnPluginVerticleConf = _

  override def initServiceAsyncS(): Future[Unit] = {
    import codecs._

    cfg = decodeConfigUnsafe[AuthnPluginVerticleConf]

    authnProviders       = buildMethodClients(cfg)
    authnEntityProviders = buildEntityClients(cfg)
    worker               = new AuthnPluginWorker(authnProviders, authnEntityProviders)

    deployProviders(cfg)
  }

  private def buildMethodClients(cfg: AuthnPluginVerticleConf) =
    cfg.methodsMapping
      .map { case (methodName, verticleId) =>
        methodName -> createClient(classOf[AuthnProvider], verticleId)
      }.toMap

  private def buildEntityClients(cfg: AuthnPluginVerticleConf) =
    cfg.entitiesMapping
      .map { case (methodName, providersMapping) =>
        methodName -> providersMapping.map { case (methodName, verticleId) =>
          methodName -> createClient(classOf[EntityProvider], verticleId)
        }.toMap
      }

  private def deployProviders(cfg: AuthnPluginVerticleConf): Future[Unit] =
    for {
      _ <- RegistryVerticle.deploy(vertx, cfg.authnMethodProvidersConfigKey.getOrElse("authn-method-providers"), false).toScala
      _ <- RegistryVerticle.deploy(vertx, cfg.authnEntityProvidersConfigKey.getOrElse("authn-entity-providers"), false).toScala
    } yield ()

  override def apply(ctx: RequestCtx, conf: AuthnPluginConf): Future[RequestCtx] =
    worker.apply(ctx, conf)

  override def validate(conf: AuthnPluginConf): ValidateResponse =
    worker.validate(conf)

  override def convertOpenApi(openApi: Swagger, rule: OpenApiRule, conf: AuthnPluginConf): ConvertOpenApiResponse = {
    if (cfg.openApi.isDefined)
      AuthnPluginOpenApiConverter.convert(openApi, rule, conf, cfg.openApi.get)
    else
      ConvertedOpenApi(openApi)
  }


}

/**
  * Provides implementation of AuthPlugin.apply.
  * This code is pulled out from AuthPlugin to make it testable - ServiceVerticles can't have constructor arguments.
  */
class AuthnPluginWorker(
  authnProviders: Map[AuthnMethodName, AuthnProvider],
  authnEntityProviders: Map[AuthnMethodName, Map[AuthnEntityType, EntityProvider]]
)(implicit ec: VertxExecutionContext) extends FutureConversions {

  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  def apply(requestCtx: RequestCtx, conf: AuthnPluginConf): Future[RequestCtx] = {
    val program: Future[AuthnPluginError \/ (AuthnCtx, Modify)] = {
      for {
        authenticated    <- authenticate(requestCtx, conf.methods, AuthnMethodConf(conf.tokenHeader)) |> eitherT
        entityProviders   = authnEntityProviders.getOrElse(authenticated.method, Map())
        entities         <- getEntities(requestCtx.tracingCtx, entityProviders, authenticated.ctx, conf) |> eitherT
      } yield {
        authenticated.ctx.get(AuthnCtx.TOKEN_TYPE) match {
          case Some(tokenType)  => (mergeCtx(authenticated.method, entities).updated(AuthnCtx.TOKEN_TYPE, tokenType), authenticated.modify)
          case None             => (mergeCtx(authenticated.method, entities), authenticated.modify)
        }
      }
    }.run

    program.map {
      case \/-((authnCtx, modify)) =>
        val resultAuthnCtx =
          conf.ctxKey match {
            case Some(key) => AuthnCtx(Map(key -> Json.obj(authnCtx.value.toList:_*)))
            case None      => authnCtx
          }

        val requestCtxWithAuthnCtx = requestCtx.modifyAuthnCtx(_.merge(resultAuthnCtx))
        modify.ifAuthnSucceeded(requestCtxWithAuthnCtx)
      case -\/(err) =>
        val resp =
          err match {
            case NoMatchingAuthnMethod =>
              log.debug(requestCtx.tracingCtx, s"No matching Authn method for $requestCtx")
              Errors.unauthenticated.toApiResponse()
            case AuthnFailResponse(resp, modify) =>
              modify.ifAuthnFailed(resp)
          }

        requestCtx.abort(resp)
    }.recover { case ex: Throwable =>
      log.error(requestCtx.tracingCtx, s"Unexpected error when applying $conf to $requestCtx", ex)
      requestCtx.abort(Errors.unexpected.toApiResponse())
    }
  }

  private def merge(a: AuthnCtx, b: AuthnCtx): AuthnCtx =
    AuthnCtx {
      Json.obj(a.value.toList: _*).deepMerge(Json.obj(b.value.toList: _*))
        .asObject.map(_.toMap).get // we can safely call Option.get because we are merging JsonObjects
    }

  private def mergeCtx(method: AuthnMethodName, authn: AuthnCtx): AuthnCtx =
    merge(authn, AuthnCtx(AUTHN_METHOD -> Json.fromString(method)))

  sealed trait AuthnState
    case object NoMatch extends AuthnState
    case class LastFailed(resp: ApiResponse, mod: Modify) extends AuthnState
    case class Success(method: AuthnMethodName, ctx: AuthnCtx, mod: Modify) extends AuthnState

  private def authenticate(ctx: RequestCtx, methods: List[AuthnMethodName], methodConf: AuthnMethodConf): Future[AuthnPluginError \/ Authenticated] = {
    val fold: Future[AuthnState] =
      methods.foldLeft(Future.successful[AuthnState](NoMatch)) { case (agg, method) =>
        agg.flatMap {
          case NoMatch =>
            authenticateOne(ctx, method, methodConf).map {
              case Some(AuthnSuccess(ctx, mod))  => Success(method, ctx, mod)
              case Some(AuthnFailure(resp, mod)) => LastFailed(resp, mod)
              case None                     => NoMatch
            }
          case LastFailed(f, aggMod) =>
            authenticateOne(ctx, method, methodConf).map {
              case Some(AuthnSuccess(ctx, mod))  => Success(method, ctx, aggMod.compose(mod))
              case Some(AuthnFailure(resp, mod)) => LastFailed(resp, aggMod.compose(mod))
              case None                     => LastFailed(f, aggMod)
            }
          case Success(succeededMethod, authnCtx, aggMod) =>
            Future.successful(Success(succeededMethod, authnCtx, aggMod))
        }
      }

    fold.map {
      case Success(method, authnCtx, mod) => \/-(Authenticated(method, authnCtx, mod))
      case NoMatch                   => -\/(NoMatchingAuthnMethod)
      case LastFailed(resp, mod)       => -\/(AuthnFailResponse(resp, mod))
    }
  }

  private def authenticateOne(ctx: RequestCtx, method: AuthnMethodName, methodConf: AuthnMethodConf): Future[Option[AuthnProviderResult]] =
    authnProviders.get(method) match {
      case Some(provider) =>
        for {
          authnResult <- provider.authenticate(ctx, methodConf).toScala.recover {
            case ex: Throwable =>
              log.error(ctx.tracingCtx, "Authentication method failed. Abstaining from decision.", ex)
              None
          }
          tokenType   <- provider.tokenType().toScala
        } yield {
          authnResult.map {
            case AuthnSuccess(ctx, mod) => AuthnSuccess(setTokenTypeIfMissing(tokenType, ctx), mod)
            case other                  => other
          }
        }
      case None =>
        Future.failed(new Exception(s"Missing authn provider: '$method'"))
    }

  private def setTokenTypeIfMissing(tokenType: AuthnMethodName, ctx: AuthnCtx) =
    ctx.get(AuthnCtx.TOKEN_TYPE) match {
      case Some(_) => ctx
      case None    => ctx.updated(AuthnCtx.TOKEN_TYPE, Json.fromString(tokenType))
    }

  private def getEntities(tracingCtx: TracingContext, entityProviders: Map[AuthnEntityType, EntityProvider], ctx: AuthnCtx, conf: AuthnPluginConf): Future[AuthnPluginError \/ AuthnCtx] = {
    val entities = conf.entities.getOrElse(nil)
    val optionalEntities = conf.optionalEntities.getOrElse(nil)
    val missingProviders = entities.filter(entityProviders.get(_).isEmpty)

    if (missingProviders.isEmpty) {

      val optProviders: List[Future[AuthnCtx]] = optionalEntities.flatMap(entityProviders.get).map(_.getEntity(tracingCtx, ctx).toScala.recover {
        case x: Throwable => AuthnCtx()
      })
      val providers: List[Future[AuthnCtx]] = entities.flatMap(entityProviders.get).map(_.getEntity(tracingCtx, ctx).toScala)

      Future.sequence(
         optProviders ::: providers
      ).map(_.foldLeft(AuthnCtx())(merge))
        .map(\/-(_))
    }
    else Future.failed(new Exception(s"Missing entity providers: [${missingProviders.mkString(", ")}]"))
  }

  def validate(conf: AuthnPluginConf): ValidateResponse =
    if (conf.methods.isEmpty) ValidateFailure("'methods' can not be empty")
    else {
      val missingAuthnMethods = conf.methods.filter(method => !authnProviders.keys.exists(_ == method))
      val missingEntityProviders = getMissingEntityProviders(conf)

      if (missingAuthnMethods.nonEmpty || missingEntityProviders.nonEmpty) {
        import io.circe.syntax._
        ValidateFailure(s"Missing providers for 'methods': [${missingAuthnMethods.mkString(", ")}] and 'entities': [${missingEntityProviders.asJson.noSpaces}]")
      } else ValidateOk
    }

  private def getMissingEntityProviders(conf: AuthnPluginConf): Map[AuthnMethodName, List[AuthnEntityType]] = {
    val entityProvidersForRequiredMethods: Map[AuthnMethodName, List[AuthnEntityType]] =
      conf.methods.map(method => method -> authnEntityProviders.get(method).map(_.keys.toList).getOrElse(Nil)).toMap

    entityProvidersForRequiredMethods.flatMap { case (method, availableEntities) =>
      val missingEntities = conf.entities.getOrElse(Nil) diff availableEntities
      if (missingEntities.nonEmpty)
        Some(method -> missingEntities)
      else None
    }
  }
}
