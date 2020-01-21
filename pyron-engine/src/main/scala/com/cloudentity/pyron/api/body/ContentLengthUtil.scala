package com.cloudentity.pyron.api.body

object ContentLengthUtil {
  def isBodyLimitExceeded(contentLength: Int, maxBodySizeOpt: Option[Int]): Boolean =
    maxBodySizeOpt.map(contentLength >> 10 > _).getOrElse(false) // contentLength / 1024 > maxBodySize
}
