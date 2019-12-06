package com.cloudentity.pyron.plugin.impl.bruteforce

import java.time.Instant

import com.cloudentity.pyron.plugin.util.value.ValueOrRef
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class Attempt(blocked: Boolean, timestamp: Instant, blockFor: Long)
sealed trait IdentifierSource
  case class ValueOrRefIdentifierSource(valueOrRef: ValueOrRef) extends IdentifierSource
  case class DeprecatedIdentifierSource(location: IdentifierLocation, name: String) extends IdentifierSource

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
}

object IdentifierSource {
  val DeprecatedIdentifierSourceDec = deriveDecoder[DeprecatedIdentifierSource]
  implicit lazy val IdentifierSourceDec: Decoder[IdentifierSource] = DeprecatedIdentifierSourceDec.or(ValueOrRef.ValueOrRefDecoder.map(ValueOrRefIdentifierSource))
}