package com.cloudentity.edge.service.authz

import io.circe.generic.semiauto._

case class Recovery(`type`: String, id: Option[String])
object Recovery {
  implicit lazy val RecoveryDec = deriveDecoder[Recovery]
}