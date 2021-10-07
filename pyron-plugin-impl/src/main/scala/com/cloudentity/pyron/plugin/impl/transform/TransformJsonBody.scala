package com.cloudentity.pyron.plugin.impl.transform

import com.cloudentity.pyron.domain.flow.{RequestCtx, ResponseCtx}
import com.cloudentity.pyron.plugin.util.value._
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.core.json.JsonArray

import scala.annotation.tailrec
import scala.util.matching.Regex
import scala.util.{Success, Try}

object TransformJsonBody {

  val nullIfAbsentDefaultValue = true

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
    else setJsonBody(bodyOps.set, bodyOps.remove.getOrElse(Nil), bodyOps.nullIfAbsent.getOrElse(nullIfAbsentDefaultValue))(jsonBody).toBuffer

  val arrayBracketRegex: Regex = """\[(\d+)]""".r

  def setJsonBody(set: Map[Path, JsonValueIgnoreNullIfDefault], remove: List[Path], nullIfAbsent: Boolean = nullIfAbsentDefaultValue)(body: JsonObject): JsonObject = {
    @tailrec
    def mutateBodyAttribute(body: JsonObject, bodyPath: List[String], resolvedValue: JsonValueIgnoreNullIfDefault): Unit =
      bodyPath match {
        case key :: Nil =>
          if(nullIfAbsent && !resolvedValue.ignoreNullIfAbsent) body.put(key, resolvedValue.jsonValue.map(_.rawValue).orNull)
          else resolvedValue.jsonValue match {
            case Some(jsonValue) => body.put(key, jsonValue.rawValue)
            case None => body.remove(key)
          }
        case key :: tail =>
          mutateBodyAttribute(getOrSetAndGetEmpty(body, key), tail, resolvedValue)
        case Nil => ()
      }

    @tailrec
    def removeBodyAttribute(body: Either[JsonArray, JsonObject], bodyPath: List[String]): Unit = {
      (body, bodyPath) match {
        case (Left(bodyArr), arrayBracketRegex(numberPart) :: Nil) if Try(numberPart.toInt).isSuccess =>
          val idx = numberPart.toInt
          if(idx < 0 || idx >= bodyArr.size()) ()
          else bodyArr.remove(idx)
        case (Left(bodyArr), arrayBracketRegex(numberPart) :: tail) if Try(numberPart.toInt).isSuccess =>
          val idx = numberPart.toInt
          if(idx < 0 || idx >= bodyArr.size()) ()
          else removeBodyAttribute(getOrSetAndGetEmptyArray(bodyArr, idx), tail)
        case (Left(_), _) =>
          () // Tried to dereference non-indexed element out of JSON array
        case (Right(bodyObj), key :: Nil) =>
          bodyObj.remove(key)
        case (Right(bodyObj), key :: tail) =>
          removeBodyAttribute(getOrSetAndGetEmptyObj(bodyObj, key), tail)
        case (_, Nil) => ()
      }
    }

    set.foreach { case (path, value) => mutateBodyAttribute(body, path.value, value) }
    remove.foreach(path => removeBodyAttribute(Right(body), path.value))
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

  private def getOrSetAndGetEmptyArray(body: JsonArray, key: Int): Either[JsonArray, JsonObject] =
    (Try(Option(body.getJsonArray(key))), Try(Option(body.getJsonObject(key)))) match {
      case (Success(Some(arr)), _) => Left(arr)
      case (_, Success(Some(obj))) => Right(obj)
      case _ => Right(new JsonObject())
    }

  private def getOrSetAndGetEmptyObj(body: JsonObject, key: String): Either[JsonArray, JsonObject] =
    (Try(Option(body.getJsonArray(key))), Try(Option(body.getJsonObject(key)))) match {
      case (Success(Some(arr)), _) => Left(arr)
      case (_, Success(Some(obj))) => Right(obj)
      case _ => // in case of Failure we overwrite a value that is not JsonObject
        val obj = new JsonObject()
        body.put(key, obj)
        Right(obj)
    }
}