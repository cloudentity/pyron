package com.cloudentity.edge.plugin.impl.authn.methods.oauth10

import java.security.MessageDigest
import java.util.Base64

import com.twitter.joauth.OAuthParams.OAuth1Params
import com.twitter.joauth.{Normalizer, Request}
import com.cloudentity.edge.domain.http.OriginalRequest
import com.cloudentity.tools.vertx.tracing.{LoggingWithTracing, TracingContext}
import io.vertx.core.buffer.Buffer
import io.vertx.core.logging.LoggerFactory
import scalaz.{-\/, \/, \/-}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

sealed trait OAuth10SignatureBaseStringBuilderError
case class OAuth10SignatureBaseStringBuilderFailure(ex: Throwable) extends OAuth10SignatureBaseStringBuilderError

/**
  * Wrapper for twitter request Normalizer with additional oauth_body_hash support
  * https://tools.ietf.org/id/draft-eaton-oauth-bodyhash-00.html
  *
  */
object OAuth10SignatureBaseStringBuilder {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  def build(ctx: TracingContext, request: OriginalRequest, params: OAuth10Request, conf: PublicDomainConf): OAuth10SignatureBaseStringBuilderError \/ String = {
    log.debug(ctx, s"Calculate base string, request: ${request}, params: ${params}, conf: ${conf}")
    for {
      queryParams <- getQueryParams(ctx, request)
      signature <- normalize(ctx, request, params, conf, queryParams)
    } yield signature
  }

  def getQueryParams(ctx: TracingContext, request: OriginalRequest): OAuth10SignatureBaseStringBuilderError \/ List[Request.Pair]  = {
    val queryParams = request.queryParams.toMap.map {
      case(key, value) => value.map(v => new Request.Pair(key, v))
    }.flatten.toList

    request.bodyOpt match {
      case Some(body) if body.getBytes.size > 0 =>
        getBodyHash(body) match {
          case Success(hash) => \/-(queryParams :+ new Request.Pair("oauth_body_hash", hash))
          case Failure(ex) => -\/(OAuth10SignatureBaseStringBuilderFailure(ex))
        }
      case _ => \/-(queryParams)
    }
  }

  def normalize(ctx: TracingContext, request: OriginalRequest, params: OAuth10Request, conf: PublicDomainConf,
                queryParams: List[Request.Pair]): OAuth10SignatureBaseStringBuilderError \/ String = {
    val scheme = if (conf.ssl) "https" else "http"
    val method = request.method.name()
    val oauth1Params = new OAuth1Params(params.token.getOrElse(null), params.consumerKey, params.nonce,
      params.timestamp, params.timestamp.toString, params.signature, params.signatureMethod, params.version.getOrElse(null))

    Try(Normalizer.getStandardNormalizer.normalize(scheme, conf.host, conf.port, method, request.path.value, queryParams.asJava, oauth1Params)) match {
      case Success(v) =>
        log.debug(ctx, s"Base string: ${v}")
        \/-(v)
      case Failure(ex) => -\/(OAuth10SignatureBaseStringBuilderFailure(ex))
    }
  }

  def getBodyHash(buffer: Buffer): Try[String] = {
    Try {
      val instance = MessageDigest.getInstance("SHA-256")
      val hash = instance.digest(buffer.getBytes)
      val encoded = Base64.getEncoder.encode(hash)
      new String(encoded, "UTF-8")
    }
  }

}
