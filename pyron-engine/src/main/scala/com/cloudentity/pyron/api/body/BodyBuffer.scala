package com.cloudentity.pyron.api.body

import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.impl.NoStackTraceThrowable
import io.vertx.core.streams.ReadStream

import scala.concurrent.{Future, Promise}

class RequestBodyTooLargeException extends NoStackTraceThrowable("Request body too large")

object BodyBuffer {
  def bufferBody(req: HttpServerRequest, contentLengthOpt: Option[Int], maxSizeOpt: Option[Int]): Future[(Option[ReadStream[Buffer]], Option[Buffer])] = {
    val p = Promise[(Option[ReadStream[Buffer]], Option[Buffer])]
    val bodyBuf = new BodyBuffer(contentLengthOpt, maxSizeOpt.getOrElse(Int.MaxValue))
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
    req.resume()
    p.future
  }
}

class BodyBuffer(contentLengthOpt: Option[Int], maxSize: Int) {
  val DEFAULT_INITIAL_BODY_BUFFER_SIZE = 1024
  val MAX_PREALLOCATED_BODY_BUFFER_BYTES = 65535

  val initBufferSize =
    contentLengthOpt match {
      case Some(contentLength) if contentLength > MAX_PREALLOCATED_BODY_BUFFER_BYTES => MAX_PREALLOCATED_BODY_BUFFER_BYTES
      case Some(contentLength) => contentLength
      case None => DEFAULT_INITIAL_BODY_BUFFER_SIZE
    }

  var buffer = Buffer.buffer(initBufferSize)
  var full = false

  def canFit(buf: Buffer): Boolean =
    (buf.length() + buffer.length()) >> 10 < maxSize

  def append(buf: Buffer): Unit =
    buffer.appendBuffer(buf)

  def isFull(): Boolean = full
  def setFull(): Unit = full = {
    buffer = null // earlier garbage collection
    true
  }
}