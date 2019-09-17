package com.cloudentity.edge.plugin.impl.authn

import java.net.{URI, URL}
import java.nio.charset.{Charset, StandardCharsets}
import java.time.{Duration, Instant, ZoneId}
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ofPattern
import java.time.temporal.ChronoField.OFFSET_SECONDS
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import com.google.common.hash.Hashing
import com.shekhargulati.urlcleaner.UrlCleaner
import com.cloudentity.edge.api.Responses.{Error, ErrorBody, Errors}
import org.apache.commons.codec.binary.Base64
import org.slf4j.LoggerFactory
import org.springframework.web.util.UriUtils

import scala.util.{Failure, Success, Try}
import scalaz.{-\/, \/, \/-}

class HmacHelper(dateFormat: String, limitInMinutes: Int, authHeaderPrefix: String) {
  val log       = LoggerFactory.getLogger(this.getClass)
  val providedFormatterWithOverriddenUTCTimeZone = DateTimeFormatter.ofPattern(this.dateFormat).withZone(ZoneId.of("UTC"))
  val providedFormatter = if (supportsZone(this.dateFormat)) ofPattern(this.dateFormat)
                          else providedFormatterWithOverriddenUTCTimeZone

  val undefinedError = Error(500, ErrorBody("undefinedError", "Hashing failed"))

  def validateDate(date: String): \/[Error, Unit] = {
    val result = for {
      instantDate <- Try(parseDate(date)).toEither.left.map(_ => Errors.invalidRequest)
      lowerBound  <- Try(Instant.now().minus(Duration.ofMinutes(this.limitInMinutes))).toEither.left.map(_ => Errors.invalidRequest)
      _ <- checkIfDateIsBefore(instantDate, lowerBound)
    } yield()

    \/.fromEither(result)
  }

  def checkIfDateIsBefore(date: Instant, lowerBound: Instant): Either[Error, Unit] = {
    if (date.isBefore(lowerBound)) {
      log.error("Hmac expired {}", date)
      return Left(Errors.hmacRequestOutdated)
    }

    Right(())
  }

  def decode(encoded: String): \/[Error, String] =
   \/.fromEither(Try(Base64.decodeBase64(encoded)).toEither).bimap(err => {
     log.error(s"Error while decoding message: ${err.getMessage}")
     undefinedError
   }, decoded => new String(decoded))


  def encode(value: String): \/[Error, String] =
    \/.fromEither(Try(Base64.encodeBase64String(value.getBytes)).toEither).bimap(err => {
      log.error(s"Error while decoding message: ${err.getMessage}")
      undefinedError
    }, encoded => new String(encoded))

  def buildSignature(key: Array[Byte], value: Array[Byte]): \/[Error, String] = {
    val hashResult = for {
      mac <- Try(Mac.getInstance("HmacSHA256"))
      _ <- Try(mac.init(new SecretKeySpec(key, "HmacSHA256")))
      encoded <- Try(mac.doFinal(value))
      basse64Enc <- Try(Base64.encodeBase64String(encoded))
    } yield basse64Enc

    \/.fromEither(hashResult.toEither).leftMap[Error](err => {
      log.error(s"Error while generating signature: ${err.getMessage}")
      undefinedError
    })
  }

  def validateSignatureMatchesAnyOfExpected(acceptableSignatures: List[String], signature: String): \/[Error, Unit] = {
    if (!(acceptableSignatures.contains(signature))) {
      log.error(s"Signature ${signature} does not match any of ${acceptableSignatures}")
      return -\/((Errors.hmacMismatch))
    }

    \/-(())
  }

  def buildAuthorizationHeader(`type`: String, uuid: String, signature: String): String = s"${`type`} ${uuid}:${signature}"

  def buildAuthorizationHeader(`type`: String, uuid: String, signature: String, realm: String): String = s"${`type`} ${uuid}:${signature}:${realm}"

  def parseAuthorizationHeader(header: String, key: Int, authHeaderPrefix: String): \/[Error, String] = {
    header.split(" ").toList match {
      case authHeader :: rest :: Nil if authHeader == authHeaderPrefix => {
        val fields = rest.split(":").toList
        if (key < fields.length) return \/-(fields(key))
        else {
          log.error(s"Invalid authorization header, no data on ${key} position")
          -\/(Errors.invalidRequest)
        }
      }
      case _ => {
        log.error(s"Invalid authorization header")
        -\/(Errors.invalidRequest)
      }
    }
  }

  def getSignature(authorizationHeader: String): \/[Error, String]  = parseAuthorizationHeader(authorizationHeader, 1, this.authHeaderPrefix)

  def getRealmOrDefault(authorizationHeader: String, defaultRealm: String): String  = {
    parseAuthorizationHeader(authorizationHeader, 2, this.authHeaderPrefix)
      .leftMap(_ => log.error(s"recovering with default realm ${defaultRealm}"))
      .getOrElse(defaultRealm)
  }

  def getUUID(authorizationHeader: String): \/[Error, String]  = parseAuthorizationHeader(authorizationHeader, 0, this.authHeaderPrefix)

  def buildRequest(method: String, body: String, date: Instant, url: String): \/[Error, String] = buildRequest(method, body, formatDate(date), url)
  def buildRequest(method: String, body: String, date: String, url: String): \/[Error, String] = {
    for {
      uri <- getCanonicalUri(url)
      qParam <- getCanonicalizedQueryParams(url)
    } yield buildRequest(method, md5(body), date, uri, qParam)
  }

  private def buildRequest(method: String, contentMd5: String, date: String, canonicalUri: String, canonicalizedQueryString: String): String = method + "\n" + contentMd5 + "\n" + date + "\n" + canonicalUri + "\n" + canonicalizedQueryString

  def md5(content: String): String = Hashing.md5.hashString(content, Charset.defaultCharset).toString

  def getCanonicalUri(url: String): \/[Error, String] = {
    Try(new URI(url)).flatMap(checkUriValidity).map(_.normalize).toEither match {
      case Right(uri) => \/-(Option(uri.getHost).getOrElse("") + handlePort(uri) + urlEncodePaths(uri))
      case Left(err) => {
        log.error(s"Hashing failed, ${err.getMessage}")
        -\/(Errors.invalidRequest)
      }
    }
  }

  def checkUriValidity(uri: URI): Try[URI] =
    if(uri.getRawPath == null) Failure(new IllegalArgumentException("url can't be empty of null."))
    else Success(uri)

  def handlePort(uri: URI): String =
    (Option(uri.getScheme).getOrElse("").toLowerCase, uri.getPort) match {
      case (_, -1) => ""
      case ("http", 80) => ""
      case ("https", 443) => ""
      case ("ftp", 21) => ""
      case _ => ":" + uri.getPort
    }

  private def urlEncodePaths(uri: URI): String = uri.getRawPath.split("/").map(encodeUrlSegment).mkString("/")

  private def encodeUrlSegment(segment: String): String = UriUtils.encodePathSegment(UriUtils.decode(segment, StandardCharsets.UTF_8.toString), StandardCharsets.UTF_8.toString)

  def getCanonicalizedQueryParams(url: String): \/[Error, String] = {
    \/.fromEither(Try(Option(new URL(UrlCleaner.normalizeUrl(url)).getQuery).getOrElse("")).toEither).leftMap[Error](err => {
      log.error(s"Hashing failed, ${err.getMessage}")
      Errors.invalidRequest
    })
  }

  private def parseDate(dateString: String) = {
    Instant.from(providedFormatter.parse(dateString))
  }

  private def formatDate(date: Instant) = {
    providedFormatterWithOverriddenUTCTimeZone.format(date)
  }

  private def supportsZone(dateFormat: String) = {
    val pattern = ofPattern(dateFormat)
    pattern.parse(pattern.withZone(ZoneId.of("UTC")).format(Instant.now())).isSupported(OFFSET_SECONDS)
  }
}
