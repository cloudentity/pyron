package com.cloudentity.pyron.api.body

import com.cloudentity.pyron.domain.rule.Kilobytes

object BodyLimit {
  def isMaxSizeExceeded(contentLengthBytes: Long, maxBodySizeOpt: Option[Kilobytes]): Boolean =
    maxBodySizeOpt.map(contentLengthBytes > _.bytes).getOrElse(false)
}
