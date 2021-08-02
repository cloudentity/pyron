package com.cloudentity.pyron.plugin.condition

import com.cloudentity.pyron.plugin.util.value.ValueResolver.ResolveCtx
import com.cloudentity.pyron.plugin.util.value.{ValueOrRef, ValueResolver}
import io.circe.generic.semiauto._
import io.circe.{Decoder, HCursor}
import io.vertx.core.json.JsonObject

sealed trait ApplyIf

case class Stringifiable(value: String)
object Stringifiable {
  implicit val StringifiableDecoder: Decoder[Stringifiable] =
    Decoder.decodeString
      .or(Decoder.decodeBoolean.map(_.toString))
      .or(Decoder.decodeJsonNumber.map(_.toString))
      .map(Stringifiable.apply)
}

object ApplyIf {
  case object Always extends ApplyIf
  case class In(array: List[Stringifiable], value: ValueOrRef) extends ApplyIf

  def evaluate(applyIf: ApplyIf, ctx: ResolveCtx): Boolean =
    applyIf match {
      case In(array, valueOrRef) =>
        val valueOpt = ValueResolver.resolveString(ctx, new JsonObject(), valueOrRef)
        valueOpt.map(value => array.exists(_.value == value)).getOrElse(false)
      case Always => true
    }

  implicit val InDecoder: Decoder[In] = (c: HCursor) => c.get("in")(deriveDecoder[In])
  implicit val ApplyIfDecoder: Decoder[ApplyIf] = InDecoder.map[ApplyIf](identity)
}
