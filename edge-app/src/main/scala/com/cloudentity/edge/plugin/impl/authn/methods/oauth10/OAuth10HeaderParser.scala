package com.cloudentity.edge.plugin.impl.authn.methods.oauth10

import java.net.URLDecoder

import scala.util.Try

object OAuth10HeaderParser {

  def parse(headerValue: String): Try[Map[String, String]] = {
    Try {
      headerValue.split(",")
        .filter(s => s.contains("=\""))
        .map(param => param.trim)
        .map { keyValue =>
          val parts = keyValue.split("=\"")
          (urlDecode(parts(0)), urlDecode(parts(1).dropRight(1)))
        }.toMap
    }
  }

  def urlDecode(s: String): String = URLDecoder.decode(s, "UTF-8")

}
