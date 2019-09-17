package com.cloudentity.edge.plugin.impl.authn.methods.oauth10

import org.junit.Test
import org.scalatest.MustMatchers

import scala.util.{Failure, Success, Try}

class OAuth10HeaderParserTest extends MustMatchers {

  @Test
  def parseEmptyParams(): Unit = {
    val res = OAuth10HeaderParser.parse("")
    assertSuccess(res) { params =>
      params.size must be (0)
    }
  }

  @Test
  def parseSampleHeader(): Unit = {
    val headerValue = "oauth_consumer_key=\"E3IZ0MqBDaU7UnUSJ4GFZF26VCcUGb5oWUg8usJof9e1528c%21015c15548a0c4a9689ec7f689ee6a5c20000000000000000\",oauth_signature=\"Kyl05GK2VvVYg4zFxgq2vzvNJjnMiGLG3soWDh1UzBLoUFGmhkCgfKGyfBFEodtEAaGMLNX%2F89BrFbzQRZDXLo%2BfTl3xIpbyTR5g4i8u1J2vs64wRghd0kDL80K7TP92Div9Wm%2BKAdt70euRGnOhtBAPV5oNg8jNkIkGmu1At72dVA8yjOi5rvy0t4PGso2GncKYJFwp007X208rN8YRzNKpeiRhxcEeop4Voqa6lEWuVvmZUZCG25ovkSRJSfIQLAF7SXtcRh2%2B%2BKBQJ7Q%2B1V%2FvsKDwE3DHwInTxrz2lFiyfJ%2BcO1cvZlHY3RvTlt7b4w%2Fu%2BOJSg3RjNPPad74vnw%3D%3D\",oauth_signature_method=\"RSA-SHA256\",oauth_nonce=\"bkkg0ub5i1723e67rtnssnafh\",oauth_timestamp=\"1531142256\",oauth_version=\"1.0\""
    val res = OAuth10HeaderParser.parse(headerValue)

    assertSuccess(res) { params =>
      params.get("oauth_consumer_key") must be (Some("E3IZ0MqBDaU7UnUSJ4GFZF26VCcUGb5oWUg8usJof9e1528c!015c15548a0c4a9689ec7f689ee6a5c20000000000000000"))
      params.get("oauth_signature") must be (Some("Kyl05GK2VvVYg4zFxgq2vzvNJjnMiGLG3soWDh1UzBLoUFGmhkCgfKGyfBFEodtEAaGMLNX/89BrFbzQRZDXLo+fTl3xIpbyTR5g4i8u1J2vs64wRghd0kDL80K7TP92Div9Wm+KAdt70euRGnOhtBAPV5oNg8jNkIkGmu1At72dVA8yjOi5rvy0t4PGso2GncKYJFwp007X208rN8YRzNKpeiRhxcEeop4Voqa6lEWuVvmZUZCG25ovkSRJSfIQLAF7SXtcRh2++KBQJ7Q+1V/vsKDwE3DHwInTxrz2lFiyfJ+cO1cvZlHY3RvTlt7b4w/u+OJSg3RjNPPad74vnw=="))
      params.get("oauth_signature_method") must be (Some("RSA-SHA256"))
      params.get("oauth_timestamp") must be (Some("1531142256"))
      params.get("oauth_version") must be (Some("1.0"))
    }
  }

  @Test
  def parseHeaderWithParamsSeparatedWithSpace(): Unit = {
    val headerValue = "oauth_consumer_key=\"123\", oauth_signature_method=\"RSA-SHA256\""
    val res = OAuth10HeaderParser.parse(headerValue)
    assertSuccess(res) { params =>
      params.size must be(2)
    }
  }

  def assertSuccess(res: Try[Map[String, String]])(successBlock: Map[String, String] => Unit) = {
    res match {
      case Success(params) => successBlock(params)
      case Failure(_) => fail("Fail")
    }
  }

}
