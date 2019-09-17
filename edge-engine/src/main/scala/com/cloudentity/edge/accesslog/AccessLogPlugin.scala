package com.cloudentity.edge.accesslog

import io.circe.generic.semiauto._
import io.circe.Decoder
import io.circe.parser._
import com.cloudentity.edge.accesslog.AccessLogHelper.{AccessLogConf, LogAllFields, LogWhitelistedFields, MaskFieldsConf}
import com.cloudentity.edge.accesslog.AccessLogPlugin.AccessLogPluginConf
import com.cloudentity.edge.commons.BodyUtils
import com.cloudentity.edge.domain.flow.{PluginName, RequestCtx}
import com.cloudentity.edge.plugin.RequestPluginService
import com.cloudentity.edge.plugin.config.{ValidateOk, ValidateResponse}
import com.cloudentity.edge.plugin.verticle.RequestPluginVerticle
import com.cloudentity.edge.util.ConfigDecoder
import io.vertx.core.json.JsonObject

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object AccessLogPlugin extends AccessLogHelper {

  case class AccessLogPluginConf(request: Option[RequestBodyLogConf])
  case class RequestBodyLogConf(body: Option[AccessLogConf])

  implicit val accessLogPluginConfDecoder: Decoder[AccessLogPluginConf] = deriveDecoder[AccessLogPluginConf]
  implicit val requestBodyLogConfDecoder: Decoder[RequestBodyLogConf] = deriveDecoder[RequestBodyLogConf]
}

class AccessLogPlugin extends RequestPluginVerticle[AccessLogPluginConf] with RequestPluginService with ConfigDecoder with BodyUtils with AccessLogHelper {
  override def name: PluginName = PluginName("accessLog")

  override def apply(requestCtx: RequestCtx, pluginConf: AccessLogPluginConf): Future[RequestCtx] = {
    val bodyOpt = requestCtx.request.bodyOpt

    // bodyOpt is always defined even with empty body, so checking length != 0 instead
    if (bodyOpt.isEmpty || bodyOpt.get.length() == 0) return Future.successful(requestCtx)

    val body = Try(bodyOpt.get.toJsonObject) match {
      case Success(value) => value
      case Failure(ex) => return Future.successful(requestCtx)
    }

    val updatedBody = pluginConf.request.flatMap(_.body).map { conf =>
      val maskFields = conf.maskFields.getOrElse(MaskFieldsConf(None, None))

      conf.typeConf match {
        case LogAllFields(true) => pruneBody(body, List(), maskFields)
        case LogWhitelistedFields(paths) => pruneBody(body, body.fieldNames().asScala.toList diff paths, maskFields)
        case _ => new JsonObject()
      }
    }

    val bodyLog = new JsonObject().put("body", updatedBody.get)

    parse(bodyLog.encode()) match {
      case Right(json) => Future.successful(requestCtx.modifyAccessLog(_.updated("request", json)))
      case Left(ex) => Future.failed(ex)
    }
  }

  private def pruneBody(body: JsonObject, fields: List[String], maskFields: MaskFieldsConf): JsonObject = {
    fields.foldLeft(body) { (acc, dropField) => {
      acc.remove(dropField)
      acc
    }}

    maskBodyFields(body, maskFields)
  }

  private def maskBodyFields(body: JsonObject, maskFields: MaskFieldsConf): JsonObject = {
    val bodyFields = body.fieldNames().asScala.toList
    maskFields.partial.getOrElse(List()).foreach { fieldToMask => {
      if (bodyFields.contains(fieldToMask))
        if (body.getValue(fieldToMask).isInstanceOf[String])
          body.put(fieldToMask, maskPartially(body.getString(fieldToMask)))
        else
          body.put(fieldToMask, "******")
      else body
    }}

    maskFields.whole.getOrElse(List()).foreach { fieldToHide => {
      if (bodyFields.contains(fieldToHide)) body.put(fieldToHide, "******") else body
    }}

    body
  }

  override def validate(conf: AccessLogPluginConf): ValidateResponse = ValidateOk

  override def confDecoder: Decoder[AccessLogPluginConf] = deriveDecoder[AccessLogPluginConf]
}
