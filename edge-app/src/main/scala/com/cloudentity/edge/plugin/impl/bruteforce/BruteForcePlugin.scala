package com.cloudentity.edge.plugin.impl.bruteforce

import java.time.Instant

import io.circe.generic.semiauto._
import io.circe.parser._
import io.circe.{Decoder, Encoder, Json}
import com.cloudentity.edge.api.Responses
import com.cloudentity.edge.plugin.config._
import com.cloudentity.edge.domain._
import com.cloudentity.edge.domain.flow.{PluginName, RequestCtx, ResponseCtx}
import com.cloudentity.edge.domain.http.{ApiResponse, TargetRequest}
import com.cloudentity.edge.plugin.verticle.{PluginState, RequestResponsePluginVerticle}
import com.cloudentity.edge.util.ConfigDecoder
import com.cloudentity.tools.vertx.hazelcast.{HazelcastService, HazelcastServiceClient}
import com.cloudentity.tools.vertx.scala.Operation
import com.cloudentity.tools.vertx.http.Headers
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.{Future => VxFuture}

import scala.concurrent.Future
import scala.concurrent.duration._
import scalaz.{-\/, \/-}

sealed trait IdentifierLocation
case object HeaderIdentifier extends IdentifierLocation
case object BodyIdentifier extends IdentifierLocation

object IdentifierLocation {
  implicit val IdentifierLocationDec: Decoder[IdentifierLocation] =
    Decoder.decodeString.emap {
      case "header" => Right(HeaderIdentifier)
      case "body" => Right(BodyIdentifier)
      case x => Left(s"Unsupported brute-force identifier location: '$x'")
    }
}

case class BruteForceIdentifier(location: IdentifierLocation, name: String)
case class BruteForcePluginState(attempts: List[BruteForceAttempt], identifier: String)

object BruteForceIdentifier {
  implicit val BruteForceIdentifierDec = deriveDecoder[BruteForceIdentifier]
}

case class BruteForceConfig(
  maxAttempts: Int,
  blockSpan: Int,
  blockFor: Int,
  successCodes: List[Int],
  errorCodes: List[Int],
  identifier: BruteForceIdentifier,
  lockedResponse: Json,
  counterName: String
)

case class BruteForceAttempt(blocked: Boolean, timestamp: Instant, blockFor: Long)

object BruteForceAttempt {
  implicit val InstantDecoder: Decoder[Instant] = Decoder.decodeLong.map(Instant.ofEpochMilli)
  implicit val InstantEncoder: Encoder[Instant] = Encoder.encodeLong.contramap(_.toEpochMilli)

  implicit val BruteForceAttemptDecoder: Decoder[BruteForceAttempt] = deriveDecoder
  implicit val BruteForceAttemptEncoder: Encoder[BruteForceAttempt] = deriveEncoder
}

case class BruteForcePluginConfig(cacheTimeoutMs: Option[Int], leaseDurationMs: Option[Int])

object BruteForcePlugin {
  val cacheCollectionPrefix = "bruteForceAttempts_"
}

class BruteForcePlugin extends RequestResponsePluginVerticle[BruteForceConfig] with PluginState[BruteForcePluginState] with BruteForceEvaluator with ConfigDecoder {
  var cache: HazelcastServiceClient = _
  var cacheLockLeaseDuration: Duration = _

  override def name: PluginName = PluginName("bruteForce")
  override def validate(conf: BruteForceConfig): ValidateResponse = ValidateOk
  override def confDecoder: Decoder[BruteForceConfig] = deriveDecoder

  implicit lazy val pluginConfDecoder: Decoder[BruteForcePluginConfig] = deriveDecoder

  override def initService(): Unit = {
    val conf = decodeConfigOptUnsafe(BruteForcePluginConfig(None, None))
    cache = HazelcastServiceClient(createClient(classOf[HazelcastService], new DeliveryOptions().setSendTimeout(conf.cacheTimeoutMs.getOrElse(30000).toLong)))
    cacheLockLeaseDuration = conf.leaseDurationMs.getOrElse(60000) millis
  }

  def attemptsCollectionName(counterName: String): String = BruteForcePlugin.cacheCollectionPrefix + counterName
  def locksCollectionName(counterName: String): String = BruteForcePlugin.cacheCollectionPrefix + counterName + ".lock"

  def blockedResponse(body: Json) = ApiResponse(423, Buffer.buffer(body.noSpaces), Headers.of("Content-Type" -> "application/json"))

  sealed trait BruteForceResult
  case object LockAlreadyAcquired extends BruteForceResult
  case object BruteForceBlocked extends BruteForceResult
  case class FailureWithoutLock(ex: Throwable) extends BruteForceResult

  override def apply(ctx: RequestCtx, conf: BruteForceConfig): Future[RequestCtx] = {
    IdentifierReader.read(ctx.request, conf.identifier) match {
      case Some(identifier) =>
        val counterName = conf.counterName
        val collection = attemptsCollectionName(counterName)

        /* Dealing with lock
         * - unlock only if lock was acquired
         * - unlock right away if attempt is blocked
         * - unlock after response is back if target service was called
         * - unlock at the end of Futures' flat-map sequence if exception occurred
         *
         * NOTE:
         * We are not using locks provided by Hazelcast, because the lock owner is the locking thread.
         * So if we were locking when processing request on one thread then it's very likely we would be processing response
         * on different thread, so in order to update brute-force attempt we would wait for the lock to be released (what would happen only if lease time expired).
         *
         * Instead of Hazelcast locking we are using put operation on separate 'lock' collection. If put returns empty value it means that lock was not acquired.
         * In order to release 'lock' we remove record from 'lock' collection.
         *
         * E.g. if we want to lock key X in 'bruteForce_identifierpassword' then we put a marker value at key X in 'bruteForce_identifierpassword.lock' collection.
         */
        val program: Future[BruteForceResult \/ RequestCtx] = {
          for {
            lockAcquired <- tryLockWithLeaseTime(counterName, identifier, cacheLockLeaseDuration).toOperation[BruteForceResult].recover(ex => -\/(FailureWithoutLock(ex)))
            _            <- if (lockAcquired) Operation.success[BruteForceResult, Unit](()) else Operation.error[BruteForceResult, Unit](LockAlreadyAcquired)
            _             = log.debug(ctx.tracingCtx, s"Lock $collection.$identifier acquired")
            attemptsOpt  <- cache.getValue[List[BruteForceAttempt]](collection, identifier).toOperation[BruteForceResult]
            attempts      = attemptsOpt.getOrElse(Nil)
            _             = log.debug(ctx.tracingCtx, s"Attempts for $collection.$identifier: $attempts")
            blocked       = isBlocked(attempts)
            _            <- if (!blocked) Operation.success[BruteForceResult, Unit](()) else Operation.error[BruteForceResult, Unit](BruteForceBlocked)
          } yield {
            ctx.withPluginState(BruteForcePluginState(attempts, identifier))
          }
        }.run

        program.flatMap {
          case \/-(ctx) =>
            log.debug(ctx.tracingCtx, s"Passing attempt: $conf")
            ctx |> Future.successful

          case -\/(LockAlreadyAcquired) =>
            log.warn(ctx.tracingCtx, s"Lock already acquired on brute-force attempts cache: $conf")
            ctx.abort(blockedResponse(conf.lockedResponse)) |> Future.successful

          case -\/(BruteForceBlocked) =>
            log.debug(ctx.tracingCtx, s"Brute-force attempt blocked: $conf")
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
      case None => Future.successful(ctx)
    }
  }

  override def apply(ctx: ResponseCtx, conf: BruteForceConfig): Future[ResponseCtx] =
    ctx.getPluginState() match {
      case Some(state) =>
        updateAttempts(ctx.tracingCtx, ctx.response, state.attempts, conf, state.identifier)
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

    private def updateAttempts(ctx: TracingContext, resp: ApiResponse, attempts: List[BruteForceAttempt], conf: BruteForceConfig, identifier: String): Future[Unit] = {
      val counterName = conf.counterName
      if (conf.successCodes.contains(resp.statusCode)) {
        log.debug(ctx, s"Response successful for $counterName.$identifier")
        if (attempts.nonEmpty) cache.removeValue(attemptsCollectionName(counterName), identifier).toScala()
        else Future.successful(())
      } else if (conf.errorCodes.contains(resp.statusCode)) {
        log.debug(ctx, s"Response failure for $counterName.$identifier")
        val should = shouldBeBlocked(attempts, conf.maxAttempts, conf.blockSpan)
        updateBruteForceAttempts(BruteForceAttempt(should, Instant.now, conf.blockFor), counterName, identifier, should, attempts, conf)
      } else {
        Future.successful(())
      }
    }

  private def updateBruteForceAttempts(attempt: BruteForceAttempt, counterName: String, identifier: String, shouldBeBlocked: Boolean, attempts: List[BruteForceAttempt], conf: BruteForceConfig): Future[Unit] = {
    val pastBlockTime = Instant.now.minusSeconds(conf.blockFor)
    val relevantAttempts = attempts.filter(_.timestamp.isAfter(pastBlockTime))
    cache.setValue(attemptsCollectionName(counterName), identifier, relevantAttempts ::: List(attempt), (conf.blockSpan + conf.blockFor) seconds)
  }.toScala

  private def tryLockWithLeaseTime(counterName: String, key: String, leaseTime: Duration): VxFuture[Boolean] = {
    cache.putValue(locksCollectionName(counterName), key, "locked", leaseTime).compose { oldValueOpt =>
      VxFuture.succeededFuture(oldValueOpt.isEmpty)
    }
  }

  private def unlockCache(ctx: TracingContext, counterName: String, identifier: String): Future[Unit] = {
    log.debug(ctx, s"Unlocking cache: $counterName.$identifier")
    cache.removeValue(locksCollectionName(counterName), identifier).toScala()
  }

  private def shouldBeBlocked(attempts: List[BruteForceAttempt], maxAttempts: Int, blockSpan: Int): Boolean = {
    val blockSpanStart = Instant.now.minusSeconds(blockSpan)
    val failedAttemptsInBlockSpan = attempts.map(bfa => bfa.timestamp).count(_.isAfter(blockSpanStart))
    failedAttemptsInBlockSpan >= (maxAttempts - 1)
  }
}

object IdentifierReader {
  def read(req: TargetRequest, id: BruteForceIdentifier): Option[String] =
    id.location match {
      case BodyIdentifier   => readIdentifierFromBody(req, id)
      case HeaderIdentifier => req.headers.get(id.name)
    }

  private def readIdentifierFromBody(req: TargetRequest, id: BruteForceIdentifier) =
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
