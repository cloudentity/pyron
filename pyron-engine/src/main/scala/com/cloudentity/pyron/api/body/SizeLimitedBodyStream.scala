package com.cloudentity.pyron.api.body

import com.cloudentity.pyron.domain.rule.Kilobytes
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream

object SizeLimitedBodyStream {

  def apply(from: ReadStream[Buffer], maxSize: Kilobytes): ReadStream[Buffer] = {
    new ReadStream[Buffer] {
      var exHandler: Handler[Throwable] = null
      var size = 0
      var failed = false

      override def handler(h: Handler[Buffer]): ReadStream[Buffer] = {
        from.handler { buf =>
          if (!failed) { // still 'from' produces items
            size += buf.length()
            if (size > maxSize.bytes) {
              failed = true
              exHandler.handle(new RequestBodyTooLargeException)
            } else {
              h.handle(buf)
            }
          }
        }
        this
      }

      override def pause(): ReadStream[Buffer] = {
        from.pause()
        this
      }

      override def resume(): ReadStream[Buffer] = {
        from.resume()
        this
      }

      override def fetch(amount: Long): ReadStream[Buffer] = {
        from.fetch(amount)
        this
      }

      override def endHandler(endHandler: Handler[Void]): ReadStream[Buffer] = {
        from.endHandler(endHandler)
        this
      }

      override def exceptionHandler(handler: Handler[Throwable]): ReadStream[Buffer] = {
        val h = this.exHandler
        this.exHandler = { ex =>
          if (h != null) h.handle(ex)
          if (handler != null) handler.handle(ex)
        }
        from.exceptionHandler(handler)
        this
      }
    }
  }

}
