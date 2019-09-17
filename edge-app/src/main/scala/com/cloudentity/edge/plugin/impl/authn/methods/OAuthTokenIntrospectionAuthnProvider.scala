package com.cloudentity.edge.plugin.impl.authn.methods

import java.net.URLEncoder
import java.util.Base64

import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Json, JsonObject}
import com.cloudentity.edge.domain.{http, _}
import com.cloudentity.edge.plugin.impl.authn.AuthnPlugin.{AuthnFailure, AuthnProviderResult, AuthnSuccess}
import com.cloudentity.edge.plugin.impl.authn.{AuthnMethodConf, AuthnProvider}
import com.cloudentity.edge.util.ConfigDecoder
import com.cloudentity.tools.vertx.http.{SimpleSmartHttpClient, SmartHttpClient}
import com.cloudentity.tools.vertx.scala.Operation
import com.cloudentity.tools.vertx.scala.bus.ScalaServiceVerticle
import com.cloudentity.tools.vertx.tracing.LoggingWithTracing
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.{JsonObject => VxJsonObject}
import io.vertx.core.{Future => VxFuture}
import com.cloudentity.edge.commons.ClientWithTracing._
import com.cloudentity.edge.domain.authn.CloudentityAuthnCtx
import com.cloudentity.edge.domain.flow.{AuthnCtx, RequestCtx}
import com.cloudentity.edge.domain.http.ApiResponse
import com.cloudentity.tools.vertx.http.Headers

import scala.concurrent.Future
import scalaz.{-\/, \/-}

import scala.util.Try
import scala.util.matching.Regex

case class OAuthTokenIntrospectionTokenConf(header: String, regex: Regex)
case class OAuthTokenIntrospection(httpClient: JsonObject, endpointPath: String, clientId: String, clientSecret: String, token: OAuthTokenIntrospectionTokenConf, extraFormParameters: Option[Map[String, String]])

class OAuthTokenIntrospectionAuthnProvider extends ScalaServiceVerticle with AuthnProvider with ConfigDecoder {
  val log: LoggingWithTracing = LoggingWithTracing.getLogger(this.getClass)

  override def vertxServiceAddressPrefixS: Option[String] = Option(verticleId())

  var myConf: OAuthTokenIntrospection = _
  var httpClient: SmartHttpClient = _
  var authzHeader: String = _

  implicit val regexDecoder = Decoder.decodeOption[String].map { patternOpt =>
    patternOpt match {
      case Some(pattern) => pattern.r
      case None          => "Bearer (.*)".r
    }
  }

  override def initService(): Unit = {
    myConf = decodeConfigUnsafe[OAuthTokenIntrospection]
    authzHeader = s"Basic ${Base64.getEncoder.encodeToString(s"${myConf.clientId}:${myConf.clientSecret}".getBytes)}"
    httpClient = SimpleSmartHttpClient.create(vertx, new VxJsonObject(myConf.httpClient.asJson.toString())).toTry.get
  }

  override def authenticate(req: RequestCtx, methodConf: AuthnMethodConf): VxFuture[Option[AuthnProviderResult]] = {
    val tokenOpt: Option[String] =
      for {
        header <- req.request.headers.get(methodConf.tokenHeader.getOrElse(myConf.token.header))
        regexMatch <- myConf.token.regex.findFirstMatchIn(header)
        token <- Try(regexMatch.group(1)).toOption
      } yield (token)

    tokenOpt match {
      case Some(token) =>
        val call =
          httpClient.post(myConf.endpointPath)
            .putHeader("Content-Type", "application/x-www-form-urlencoded")
            .putHeader("Authorization", authzHeader)
            .withTracing(req.tracingCtx)
            .endWithBody(req.tracingCtx, buildIntrospectionForm(token))

        val program: Operation[Exception, AuthnProviderResult] =
          for {
            response    <- call.toOperation[Exception]
            body        <- parse(response.getBody.toString).toOperation.leftMap[Exception](identity)
            _            = log.debug(req.tracingCtx, s"Received response from Authorization Server: ${body.noSpaces}")
            jsonObject  <- body.asObject.toOperation(new Exception("Expected JSON object in response body"))
          } yield {
            getActiveAttribute(jsonObject) match {
              case Some(active) =>
                if (active) AuthnSuccess(AuthnCtx(jsonObject.toMap.updated(CloudentityAuthnCtx.TOKEN, Json.fromString(token))))
                else        AuthnFailure(ApiResponse(401, Buffer.buffer(), Headers()))
              case None =>
                log.error(req.tracingCtx, "Could not find 'active' attribute")
                AuthnFailure(http.ApiResponse(401, Buffer.buffer(), Headers()))
            }
          }

          program.map(Some(_)).run.flatMap[Option[AuthnProviderResult]] {
            case \/-(result) => Future.successful(result)
            case -\/(ex)     => Future.failed(ex)
          }.toJava
      case None => VxFuture.succeededFuture(None)
    }
  }

  private def buildIntrospectionForm(token: String): String = {
    val params = myConf.extraFormParameters.getOrElse(Map[String, String]()).updated("token", token)
    params.map { case (key, value) => s"$key=${URLEncoder.encode(value, "UTF-8")}"}.mkString("&")
  }

  private def getActiveAttribute(jsonObject: JsonObject): Option[Boolean] =
    jsonObject.toMap.get("active").flatMap(_.asBoolean)

  override def tokenType(): VxFuture[String] = VxFuture.succeededFuture("accessTokenOAuth2")
}
