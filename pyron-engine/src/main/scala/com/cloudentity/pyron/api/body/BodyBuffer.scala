package com.cloudentity.pyron.api.body

import com.cloudentity.pyron.domain.rule.Kilobytes
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.impl.NoStackTraceThrowable
import io.vertx.core.streams.ReadStream

import scala.concurrent.{Future, Promise}

class RequestBodyTooLargeException extends NoStackTraceThrowable("Request body too large")

object BodyBuffer {
  def bufferBody(req: HttpServerRequest, contentLengthOpt: Option[Long], maxSizeOpt: Option[Kilobytes]): Future[(Option[ReadStream[Buffer]], Option[Buffer])] = {
    val p = Promise[(Option[ReadStream[Buffer]], Option[Buffer])]

    maxSizeOpt match {
      case Some(maxSize) =>
        val bodyBuf = new BodyBuffer(contentLengthOpt, maxSize)
        req.handler { buf =>
          if (bodyBuf.isFull()) {
            // do not append, buffer already full
          } else if (bodyBuf.canFit(buf)) {
            bodyBuf.append(buf)
          } else {
            bodyBuf.setFull
            p.failure(new RequestBodyTooLargeException())
          }
        }.endHandler { _ =>
          if (bodyBuf.isFull()) () // do nothing, promise already failed
          else p.success((None, Some(bodyBuf.buffer)))
        }.exceptionHandler { ex =>
          if (!p.isCompleted) p.failure(ex)
        }
      case None =>
        req.bodyHandler(buf => p.success((None, Some(buf)))).exceptionHandler(p.failure)
    }


    req.resume()
    p.future
  }
}

class BodyBuffer(contentLengthOpt: Option[Long], maxSize: Kilobytes) {
  val DEFAULT_INITIAL_BODY_BUFFER_SIZE = 1024
  val MAX_PREALLOCATED_BODY_BUFFER_BYTES = 65535

  val initBufferSize =
    contentLengthOpt match {
      case Some(contentLength) if contentLength > MAX_PREALLOCATED_BODY_BUFFER_BYTES => MAX_PREALLOCATED_BODY_BUFFER_BYTES
      case Some(contentLength) => Math.min(contentLength, Integer.MAX_VALUE).toInt
      case None => DEFAULT_INITIAL_BODY_BUFFER_SIZE
    }

  var buffer = Buffer.buffer(initBufferSize)
  var full = false

  def canFit(buf: Buffer): Boolean =
    buffer.length() + buf.length() <= maxSize.bytes

  def append(buf: Buffer): Unit =
    buffer.appendBuffer(buf)

  def isFull(): Boolean = full
  def setFull(): Unit = full = {
    buffer = null // earlier garbage collection
    true
  }
}