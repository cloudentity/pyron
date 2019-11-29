package com.cloudentity.pyron.plugin.impl.bruteforce

import java.time.Instant

import com.cloudentity.pyron.api.Responses
import com.cloudentity.pyron.domain.flow.{PluginName, RequestCtx, ResponseCtx}
import com.cloudentity.pyron.domain.http.{ApiResponse, TargetRequest}
import com.cloudentity.pyron.plugin.config._
import com.cloudentity.pyron.plugin.verticle.{PluginState, RequestResponsePluginVerticle}
import com.cloudentity.pyron.util.ConfigDecoder
import com.cloudentity.tools.vertx.http.Headers
import com.cloudentity.tools.vertx.scala.Operation
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.circe.generic.semiauto._
import io.circe.parser._
import io.circe.{Decoder, Encoder, Json}
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.DeliveryOptions
import scalaz.{-\/, \/-}
import io.circe.syntax._

import scala.concurrent.Future
import scala.concurrent.duration._

case class BruteForceConfig(
  maxAttempts: Int,
  blockSpan: Int,
  blockFor: Int,
  successCodes: List[Int],
  errorCodes: List[Int],
  identifier: IdentifierSource,
  lockedResponse: Json,
  counterName: String
)

case class BruteForcePluginConfig(cacheTimeoutMs: Option[Int], leaseDurationMs: Option[Int])
case class BruteForcePluginState(attempts: List[Attempt], identifier: String)

class BruteForcePlugin extends RequestResponsePluginVerticle[BruteForceConfig] with PluginState[BruteForcePluginState] with ConfigDecoder {
  var cache: BruteForceCache = _
  var cacheLockLeaseDuration: Duration = _

  override def name: PluginName = PluginName("bruteForce")
  override def validate(conf: BruteForceConfig): ValidateResponse = ValidateResponse.ok()
  override def confDecoder: Decoder[BruteForceConfig] = deriveDecoder

  implicit lazy val confEncoder: Encoder[BruteForceConfig] = deriveEncoder
  implicit lazy val pluginConfDecoder: Decoder[BruteForcePluginConfig] = deriveDecoder

  override def initService(): Unit = {
    val conf = decodeConfigOptUnsafe(BruteForcePluginConfig(None, None))
    cache = createClient(classOf[BruteForceCache], new DeliveryOptions().setSendTimeout(conf.cacheTimeoutMs.getOrElse(30000).toLong))
    cacheLockLeaseDuration = conf.leaseDurationMs.getOrElse(60000) millis
  }

  def blockedResponse(body: Json) = ApiResponse(423, Buffer.buffer(body.noSpaces), Headers.of("Content-Type" -> "application/json"))

  sealed trait BruteForceResult
    case object LockAlreadyAcquired extends BruteForceResult
    case object BruteForceBlocked extends BruteForceResult
    case class FailureWithoutLock(ex: Throwable) extends BruteForceResult

  override def apply(ctx: RequestCtx, conf: BruteForceConfig): Future[RequestCtx] = {
    BruteForceIdentifierReader.read(ctx.request, conf.identifier) match {
      case Some(identifier) =>
        val counterName = conf.counterName
        log.debug(ctx.tracingCtx, s"Checking brute-force for $counterName.$identifier")

        val program: Future[BruteForceResult \/ RequestCtx] = {
          for {
            lockAcquired <- tryLockWithLeaseTime(ctx.tracingCtx, counterName, identifier, cacheLockLeaseDuration).toOperation[BruteForceResult].recover(ex => -\/(FailureWithoutLock(ex)))
            _            <- if (lockAcquired) Operation.success[BruteForceResult, Unit](()) else Operation.error[BruteForceResult, Unit](LockAlreadyAcquired)
            _             = log.debug(ctx.tracingCtx, s"Lock $counterName.$identifier acquired")
            attemptsOpt  <- cache.get(ctx.tracingCtx, counterName, identifier).toOperation[BruteForceResult]
            attempts      = attemptsOpt.getOrElse(Nil)
            _             = log.debug(ctx.tracingCtx, s"Attempts for $counterName.$identifier: $attempts")
            blocked       = BruteForceEvaluator.isBlocked(Instant.now(), attempts)
            _            <- if (!blocked) Operation.success[BruteForceResult, Unit](()) else Operation.error[BruteForceResult, Unit](BruteForceBlocked)
          } yield {
            ctx.withPluginState(BruteForcePluginState(attempts, identifier))
          }
        }.run

        program.flatMap {
          case \/-(ctx) =>
            if (log.isDebugEnabled) log.debug(ctx.tracingCtx, s"Passing attempt: ${conf.asJson.noSpaces}")
            ctx |> Future.successful

          case -\/(LockAlreadyAcquired) =>
            log.warn(ctx.tracingCtx, s"Lock already acquired on brute-force attempts cache: ${conf.asJson.noSpaces}")
            ctx.abort(blockedResponse(conf.lockedResponse)) |> Future.successful

          case -\/(BruteForceBlocked) =>
            log.debug(ctx.tracingCtx, s"Brute-force attempt blocked: ${conf.asJson.noSpaces}")
            unlockCache(ctx.tracingCtx, counterName, identifier)
              .map(_ => ctx.abort(blockedResponse(conf.lockedResponse)))

          case -\/(FailureWithoutLock(ex)) =>
            log.error(ctx.tracingCtx, "Brute-force plugin failed without acquiring lock", ex)
            ctx.abort(Responses.Errors.unexpected.toApiResponse()) |> Future.successful
        }.recoverWith { case ex =>
          log.error(ctx.tracingCtx, "BruteForce plugin failed", ex)
          unlockCache(ctx.tracingCtx, counterName, identifier)
          Future.failed(ex)
        }
      case None =>
        Future.successful(ctx)
    }
  }

  override def apply(ctx: ResponseCtx, conf: BruteForceConfig): Future[ResponseCtx] =
    ctx.getPluginState() match {
      case Some(state) =>
        clearOrUpdateAttempts(ctx.tracingCtx, ctx.response, state.attempts, conf, state.identifier)
          .flatMap(_ => unlockCache(ctx.tracingCtx, conf.counterName, state.identifier))
          .map(_ => ctx)
          .recoverWith { case ex =>
            log.error(ctx.tracingCtx, "BruteForce plugin failed", ex)
            unlockCache(ctx.tracingCtx, conf.counterName, state.identifier)
            Future.failed(ex)
          }
      case None =>
        Future.successful(ctx)
    }

    private def clearOrUpdateAttempts(ctx: TracingContext, resp: ApiResponse, attempts: List[Attempt], conf: BruteForceConfig, identifier: String): Future[Unit] = {
      val counterName = conf.counterName

      if (conf.successCodes.contains(resp.statusCode)) {
        log.debug(ctx, s"Response successful for $counterName.$identifier")

        if (attempts.nonEmpty) cache.clear(ctx, counterName, identifier).toScala()
        else Future.successful(())
      } else if (conf.errorCodes.contains(resp.statusCode)) {
        log.debug(ctx, s"Response failure for $counterName.$identifier")

        val now = Instant.now
        val shouldBlock = BruteForceEvaluator.shouldBlockNextAttempt(now, attempts, conf.maxAttempts, conf.blockSpan)
        updateAttempts(ctx, Attempt(shouldBlock, now, conf.blockFor), counterName, identifier, attempts, conf)
      } else {
        Future.successful(())
      }
    }

  private def updateAttempts(ctx: TracingContext, attempt: Attempt, counterName: String, identifier: String, attempts: List[Attempt], conf: BruteForceConfig): Future[Unit] = {
    val pastBlockTime = Instant.now.minusSeconds(conf.blockFor)
    val relevantAttempts = attempts.filter(_.timestamp.isAfter(pastBlockTime))
    cache.set(ctx, counterName, identifier,  relevantAttempts ::: List(attempt), (conf.blockSpan + conf.blockFor) seconds)
  }.toScala

  private def tryLockWithLeaseTime(ctx: TracingContext, counterName: String, identifier: String, leaseTime: Duration): VxFuture[Boolean] =
    cache.lock(ctx, counterName, identifier, leaseTime)

  private def unlockCache(ctx: TracingContext, counterName: String, identifier: String): Future[Unit] = {
    log.debug(ctx, s"Unlocking cache: $counterName.$identifier")
    cache.unlock(ctx, counterName, identifier).toScala()
  }
}

object BruteForceIdentifierReader {
  def read(req: TargetRequest, id: IdentifierSource): Option[String] =
    id.location match {
      case BodyIdentifier   => readIdentifierFromBody(req, id)
      case HeaderIdentifier => req.headers.get(id.name)
    }

  private def readIdentifierFromBody(req: TargetRequest, id: IdentifierSource) =
    for {
      bodyBuffer <- req.bodyOpt
      bodyJson   <- parse(bodyBuffer.toString()).toOption
      path        = id.name.split("\\.").toList
      id         <- readString(bodyJson, path)
    } yield (id)

  private def readString(json: Json, path: List[String]): Option[String] =
    path match {
      case element :: tail =>
        json.asObject.flatMap(_.apply(element)) match {
          case Some(subJson) => readString(subJson, tail)
          case None          => None
        }
      case Nil => json.asString
    }
}