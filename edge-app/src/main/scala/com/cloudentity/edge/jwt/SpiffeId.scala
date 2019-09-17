package com.cloudentity.edge.jwt

import io.circe.Json

trait SpiffeId {
  def domain: String
  def namespace: String
  def serviceAccount: String
  def versionId: String
  def instanceId: String

  def toString: String
  def toJsonFields: List[(String, Json)]
}

object SpiffeId {
  def fromString(spiffeId: String): Either[SpiffeException, SpiffeId] = {
    val it = regexp.findAllMatchIn(spiffeId)
    if (!it.hasNext)
      Left(InvalidSpiffeId("invalid spiffe id format"))
    else {
      val g = it.next()
      if (g.groupCount != 5)
        Left(InvalidSpiffeId("invalid number of groups in spiffe id"))
      else
        Right(SpiffeIdImpl(
          domain = g.group(1),
          namespace = g.group(2),
          serviceAccount = g.group(3),
          versionId = g.group(4),
          instanceId = g.group(5)
        ))
    }
  }

  val regexp = """^spiffe:\/\/(.+)\/ns\/(.+)\/sa\/(.+)\/ver\/(.+)\/ins\/(.+)$""".r

  private case class SpiffeIdImpl(
    domain: String,
    namespace: String,
    serviceAccount: String,
    versionId: String,
    instanceId: String
  ) extends SpiffeId {
    override def toString: String = s"spiffe://$domain/ns/$namespace/sa/$serviceAccount/ver/$versionId/ins/$instanceId"

    override def toJsonFields: List[(String, Json)] = List(
      ("dn", Json.fromString(domain)),
      ("ns", Json.fromString(namespace)),
      ("sa", Json.fromString(serviceAccount)),
      ("ver", Json.fromString(versionId)),
      ("ins", Json.fromString(instanceId))
    )
  }
}

sealed trait SpiffeException
case class InvalidSpiffeId(msg: String) extends SpiffeException