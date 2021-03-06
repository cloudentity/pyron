package com.cloudentity.pyron.plugin.impl.transform

import com.cloudentity.pyron.domain.flow.{RequestCtx, ResponseCtx}
import com.cloudentity.pyron.plugin.util.value._
import com.cloudentity.tools.vertx.tracing.TracingContext
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject

import scala.annotation.tailrec
import scala.util.{Success, Try}

object TransformJsonBody {

  def transformReqJsonBody(bodyOps: ResolvedBodyOps, jsonBodyOpt: Option[JsonObject])(ctx: RequestCtx): RequestCtx =
    jsonBodyOpt match {
      case Some(jsonBody) =>
        val transformedBody = applyBodyTransformations(bodyOps, jsonBody.copy())
        ctx.modifyRequest(_.copy(bodyOpt = Some(transformedBody)))
      case None =>
        ctx
    }

  def transformResJsonBody(bodyOps: ResolvedBodyOps, jsonBodyOpt: Option[JsonObject])(ctx: ResponseCtx): ResponseCtx = {
    jsonBodyOpt match {
      case Some(jsonBody) if !ctx.isFailed => // do not transform a failed response
        val transformedBody = applyBodyTransformations(bodyOps, jsonBody.copy())
        ctx.modifyResponse(_.copy(body = transformedBody))
      case None =>
        ctx
    }
  }

  def applyBodyTransformations(bodyOps: ResolvedBodyOps, jsonBody: JsonObject): Buffer =
    if (bodyOps.drop.contains(true)) Buffer.buffer()
    else setJsonBody(bodyOps.set.getOrElse(Map()))(jsonBody).toBuffer

  def setJsonBody(set: Map[Path, Option[JsonValue]])(body: JsonObject): JsonObject = {
    @tailrec
    def mutateBodyAttribute(body: JsonObject, bodyPath: List[String], resolvedValue: Option[JsonValue]): Unit =
      bodyPath match {
        case key :: Nil =>
          body.put(key, resolvedValue.map(_.rawValue).orNull)
        case key :: tail =>
          mutateBodyAttribute(getOrSetAndGetEmpty(body, key), tail, resolvedValue)
        case Nil => ()
      }

    set.foreach { case (path, value) => mutateBodyAttribute(body, path.value, value) }
    body
  }

  private def getOrSetAndGetEmpty(body: JsonObject, key: String): JsonObject =
    Try(Option(body.getJsonObject(key))) match {
      case Success(Some(obj)) => obj
      case _ => // in case of Failure we overwrite a value that is not JsonObject
        val obj = new JsonObject()
        body.put(key, obj)
        obj
    }
}