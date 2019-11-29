package com.cloudentity.pyron.plugin.impl.bruteforce

import java.time.Instant

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class Attempt(blocked: Boolean, timestamp: Instant, blockFor: Long)
case class IdentifierSource(location: IdentifierLocation, name: String)

sealed trait IdentifierLocation
  case object HeaderIdentifier extends IdentifierLocation
  case object BodyIdentifier extends IdentifierLocation

object Attempt {
  implicit lazy val InstantDecoder: Decoder[Instant] = Decoder.decodeLong.map(Instant.ofEpochMilli)
  implicit lazy val InstantEncoder: Encoder[Instant] = Encoder.encodeLong.contramap(_.toEpochMilli)

  implicit lazy val AttemptDecoder: Decoder[Attempt] = deriveDecoder
  implicit lazy val AttemptEncoder: Encoder[Attempt] = deriveEncoder
}

object IdentifierLocation {
  implicit lazy val IdentifierLocationDecoder: Decoder[IdentifierLocation] =
    Decoder.decodeString.emap {
      case "header" => Right(HeaderIdentifier)
      case "body"   => Right(BodyIdentifier)
      case x        => Left(s"Unsupported brute-force identifier location: '$x'")
    }

  implicit lazy val IdentifierLocationEncoder: Encoder[IdentifierLocation] =
    Encoder.encodeString.contramap {
      case HeaderIdentifier => "header"
      case BodyIdentifier   => "body"
    }
}

object IdentifierSource {
  implicit lazy val BruteForceIdentifierDec = deriveDecoder[IdentifierSource]
  implicit lazy val BruteForceIdentifierEnc = deriveEncoder[IdentifierSource]
}