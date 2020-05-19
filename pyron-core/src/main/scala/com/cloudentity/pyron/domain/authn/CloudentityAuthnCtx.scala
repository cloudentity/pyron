package com.cloudentity.pyron.domain.authn

import io.circe.Json
import com.cloudentity.pyron.domain.authn.CloudentityAuthnCtx._
import com.cloudentity.pyron.domain.flow.AuthnCtx

trait CloudentityAuthn {
  implicit class ToCloudentityAuthnCtx(ctx: AuthnCtx) {
    def asCloudentity() = CloudentityAuthnCtx(ctx.value)
  }
}

case class CloudentityAuthnCtx(private val ctx: Map[String, Json]) {
  def user: Option[Json] = ctx.get(USER)
  def withUser(value: Json) = CloudentityAuthnCtx(ctx = ctx + (USER -> value))
  def withoutUser = CloudentityAuthnCtx(ctx = ctx - USER)

  def userUuid: Option[String] = ctx.get(USER_UUID).flatMap(_.asString)
  def withUserUuid(value: String) = CloudentityAuthnCtx(ctx = ctx + (USER_UUID -> Json.fromString(value)))
  def withoutUserUuid = CloudentityAuthnCtx(ctx = ctx - USER_UUID)

  def realm: Option[String] = ctx.get(REALM).flatMap(_.asString)
  def withRealm(value: String) = CloudentityAuthnCtx(ctx = ctx + (REALM -> Json.fromString(value)))
  def withoutRealm = CloudentityAuthnCtx(ctx = ctx - REALM)

  def session: Option[Json] = ctx.get(SESSION)
  def withSession(value: Json) = CloudentityAuthnCtx(ctx = ctx + (SESSION -> value))
  def withoutSession = CloudentityAuthnCtx(ctx = ctx - SESSION)

  def device: Option[Json] = ctx.get(DEVICE)
  def withDevice(value: Json) = CloudentityAuthnCtx(ctx = ctx + (DEVICE -> value))
  def withoutDevice = CloudentityAuthnCtx(ctx = ctx - DEVICE)

  def deviceUuid: Option[String] = ctx.get(DEVICE_UUID).flatMap(_.asString)
  def withDeviceUuid(value: String) = CloudentityAuthnCtx(ctx = ctx + (DEVICE_UUID -> Json.fromString(value)))
  def withoutDeviceUuid = CloudentityAuthnCtx(ctx = ctx - DEVICE_UUID)

  def application: Option[Json] = ctx.get(APPLICATION)
  def withApplication(value: Json) = CloudentityAuthnCtx(ctx = ctx + (APPLICATION -> value))
  def withoutApplication = CloudentityAuthnCtx(ctx = ctx - APPLICATION)

  def applicationUuid: Option[String] = ctx.get(APPLICATION_UUID).flatMap(_.asString)
  def withApplicationUuid(value: String) = CloudentityAuthnCtx(ctx = ctx + (APPLICATION_UUID -> Json.fromString(value)))
  def withoutApplicationUuid = CloudentityAuthnCtx(ctx = ctx - APPLICATION_UUID)

  def oAuthClientId: Option[String] = ctx.get(OAUTH_CLIENT_ID).flatMap(_.asString)
  def withOAuthClientId(value: String) = CloudentityAuthnCtx(ctx = ctx + (OAUTH_CLIENT_ID -> Json.fromString(value)))
  def withoutOAuthClientId = CloudentityAuthnCtx(ctx = ctx - OAUTH_CLIENT_ID)

  def customerId: Option[String] = ctx.get(CUSTOMER_ID).flatMap(_.asString)
  def withCustomerId(value: String) = CloudentityAuthnCtx(ctx = ctx + (CUSTOMER_ID -> Json.fromString(value)))
  def withoutCustomerId = CloudentityAuthnCtx(ctx = ctx - CUSTOMER_ID)

  def token: Option[String] = ctx.get(TOKEN).flatMap(_.asString)
  def withToken(value: String) = CloudentityAuthnCtx(ctx = ctx + (TOKEN -> Json.fromString(value)))
  def withoutToken = CloudentityAuthnCtx(ctx = ctx - TOKEN)

  def authnMethod: Option[String] = ctx.get(AUTHN_METHOD).flatMap(_.asString)
  def withAuthnMethod(value: String) = CloudentityAuthnCtx(ctx = ctx + (AUTHN_METHOD -> Json.fromString(value)))
  def withoutAuthnMethod = CloudentityAuthnCtx(ctx = ctx - AUTHN_METHOD)

  def authnId: Option[String] = ctx.get(AUTHN_ID).flatMap(_.asString)
  def withAuthnId(value: String) = CloudentityAuthnCtx(ctx = ctx + (AUTHN_ID -> Json.fromString(value)))
  def withoutAuthnId = CloudentityAuthnCtx(ctx = ctx - AUTHN_ID)

  def custom(name: String): Option[Json] = ctx.get(name)
  def withCustom(name: String, value: Json) = CloudentityAuthnCtx(ctx = ctx + (name -> value))
  def withoutCustom(name: String) = CloudentityAuthnCtx(ctx = ctx - name)

  def toCtx: AuthnCtx = AuthnCtx(ctx)
}

object CloudentityAuthnCtx {
  val USER = "user"
  val USER_UUID = "userUuid"
  val REALM = "realm"
  val SESSION = "session"
  val DEVICE = "device"
  val DEVICE_UUID = "deviceUuid"
  val APPLICATION = "application"
  val APPLICATION_UUID = "applicationUuid"
  val OAUTH_CLIENT_ID = "oAuthClientId"
  val CUSTOMER_ID = "customerId"
  val TOKEN = "token"
  val AUTHN_METHOD = "authnMethod"
  val AUTHN_ID = "authnId"

  val cloudentityFields = List(USER, USER_UUID, REALM, SESSION, DEVICE, DEVICE_UUID, APPLICATION, APPLICATION_UUID, OAUTH_CLIENT_ID, CUSTOMER_ID, TOKEN, AUTHN_METHOD, AUTHN_ID)
  def build(ctx: Map[String, Json]) = CloudentityAuthnCtx(ctx)

  def apply() = new CloudentityAuthnCtx(Map())

  def apply(user: Option[Json] = None,
            userUuid: Option[String] = None,
            realm: Option[String] = None,
            session: Option[Json] = None,
            device: Option[Json] = None,
            deviceUuid: Option[String] = None,
            application: Option[Json] = None,
            applicationUuid: Option[String] = None,
            oAuthClientId: Option[String] = None,
            customerId: Option[String] = None,
            token: Option[String] = None,
            authnMethod: Option[String] = None,
            authnId: Option[String] = None,
            custom: Option[Map[String, Json]] = None): CloudentityAuthnCtx =
    new CloudentityAuthnCtx(
      List(
        user.map(USER -> _),
        userUuid.map(Json.fromString).map(USER_UUID -> _),
        realm.map(Json.fromString).map(REALM -> _),
        session.map(SESSION -> _),
        device.map(DEVICE -> _),
        deviceUuid.map(Json.fromString).map(DEVICE_UUID -> _),
        application.map(APPLICATION -> _),
        applicationUuid.map(Json.fromString).map(APPLICATION_UUID -> _),
        oAuthClientId.map(Json.fromString).map(OAUTH_CLIENT_ID -> _),
        customerId.map(Json.fromString).map(CUSTOMER_ID -> _),
        token.map(Json.fromString).map(TOKEN -> _),
        authnMethod.map(Json.fromString).map(AUTHN_METHOD -> _),
        authnId.map(Json.fromString).map(AUTHN_ID -> _)
      ).flatten.toMap ++ custom.getOrElse(Map())
    )
}