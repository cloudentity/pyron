package com.cloudentity.pyron.commons

import io.circe.parser._
import io.circe.{Error, Json, JsonObject}
import com.cloudentity.pyron.domain.http.ApiResponse
import io.vertx.core.buffer.Buffer


trait BodyUtils {

  def bodyAttributeOperation(attrName: String, attrValue: String, body: Option[JsonObject])(bodyOperation: (String, String, JsonObject) => JsonObject): JsonObject = {
    body match {
      case Some(b) => {
        bodyOperation(attrName, attrValue, b)
      }
      case None => JsonObject.empty
    }
  }

  def readBodyAttribute(attributeName: String, apiResponse: ApiResponse): Either[Error, String] =
    for {
      json <- parse(apiResponse.body.toString)
      attrValue <- json.hcursor.downField(attributeName).as[String]
    } yield attrValue

  def bodyAsJsonObject(bodyBuffer: Buffer): Either[Error, Option[JsonObject]] =
    for {
      json <- parse(bodyBuffer.toString)
      jObject <- json.as[Option[JsonObject]]
    } yield jObject

  def removeFromBody(attr: String, attrVal: String, body: JsonObject) =
    body.remove(attr)

  def addToBody(attrName: String, attrValue: String, body: JsonObject) =
    body.add(attrName, Json.fromString(attrValue))
}
