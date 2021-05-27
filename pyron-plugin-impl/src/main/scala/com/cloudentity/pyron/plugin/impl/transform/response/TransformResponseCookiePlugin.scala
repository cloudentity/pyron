package com.cloudentity.pyron.plugin.impl.transform.response

import com.cloudentity.pyron.domain.flow.{PluginName, ResponseCtx}
import com.cloudentity.pyron.domain.http.Headers
import com.cloudentity.pyron.plugin.config.{ValidateOk, ValidateResponse}
import com.cloudentity.pyron.plugin.verticle.ResponsePluginVerticle
import io.circe.Decoder
import io.netty.handler.codec.http.cookie.{ClientCookieDecoder, DefaultCookie, ServerCookieEncoder}

import scala.concurrent.Future
import scala.util.Try

class TransformResponseCookiePlugin  extends ResponsePluginVerticle[TransformResponseCookieConf] {

  import TransformResponseCookiePlugin.transformCookie

  override def name: PluginName = PluginName("transform-response-cookie-plugin")

  override def confDecoder: Decoder[TransformResponseCookieConf] =
    TransformResponseCookieConf.transformResponseCookieConfDec

  override def validate(conf: TransformResponseCookieConf): ValidateResponse = ValidateOk

  override def apply(responseCtx: ResponseCtx, conf: TransformResponseCookieConf): Future[ResponseCtx] =
    Future.successful(responseCtx.modifyResponse(resp => resp.modifyHeaders(transformCookie(_, conf))))

}

object TransformResponseCookiePlugin {

  def transformCookie(hs: Headers, conf: TransformResponseCookieConf): Headers =
    hs.getValues("Set-Cookie")
      .map(_.map(update(_, conf)))
      .map(hs.setValues("Set-Cookie", _))
      .getOrElse(hs)

  def update(cookie: String, conf: TransformResponseCookieConf): String = {
    // assume single cookie definition per Set-Cookie header
    if (cookie.startsWith(s"${conf.name}=")) {
      buildWithFilter(cookie, conf)(decoded => conf.matchesPathAndDomain(decoded.path, decoded.domain)
      ).getOrElse(cookie)
    } else cookie
  }

  def buildWithFilter(cookie: String, conf: TransformResponseCookieConf)
                     (filter: DefaultCookie => Boolean): Option[String] =
    Try(ClientCookieDecoder.STRICT.decode(cookie).asInstanceOf[DefaultCookie]).toOption
      .filter(filter).map(buildCookie(_, conf)).map(ServerCookieEncoder.STRICT.encode)

  def buildCookie(decoded: DefaultCookie, conf: TransformResponseCookieConf): DefaultCookie = {
    val name = conf.set.name.getOrElse(decoded.name)
    val value = conf.set.value.getOrElse(decoded.value)
    val cookie = new DefaultCookie(name, value)

    Option(conf.set.maxAge).foreach(_.orElse(Option(decoded.maxAge)).foreach(cookie.setMaxAge))
    Option(conf.set.path).foreach(_.orElse(Option(decoded.path)).foreach(cookie.setPath))
    Option(conf.set.domain).foreach(_.orElse(Option(decoded.domain)).foreach(cookie.setDomain))
    Option(conf.set.secure).foreach(_.orElse(Option(decoded.isSecure)).foreach(cookie.setSecure))
    Option(conf.set.httpOnly).foreach(_.orElse(Option(decoded.isHttpOnly)).foreach(cookie.setHttpOnly))
    Option(conf.set.sameSite).foreach(_.orElse(Option(decoded.sameSite)).foreach(cookie.setSameSite))
    Option(conf.set.wrap).foreach(_.orElse(Option(decoded.wrap)).foreach(cookie.setWrap))

    cookie
  }
}
