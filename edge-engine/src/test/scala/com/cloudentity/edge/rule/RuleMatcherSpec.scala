package com.cloudentity.edge.rule

import com.cloudentity.edge.domain.flow.{BasePath, EndpointMatchCriteria, PathMatching, PathParams, PathPrefix}
import io.vertx.core.http.HttpMethod
import org.junit.runner.RunWith
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{MustMatchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class RuleMatcherSpec extends WordSpec with MustMatchers with GeneratorDrivenPropertyChecks {

  import RuleMatcher._

  case class PathSuffix(value: String)

  implicit val pathPrefixArb = Arbitrary(Gen.alphaNumStr.map(PathPrefix))
  implicit val pathSuffixArb = Arbitrary(Gen.alphaNumStr.map(PathSuffix))
  implicit val methodArb = Arbitrary(Gen.oneOf(HttpMethod.values()))

  "RuleMatcher.makeMatch" should {
    "return Match when basePath, path, prefix and method IS matching" in {
      forAll(minSuccessful(10)) { (method: HttpMethod, prefix: PathPrefix, suffix: PathSuffix) =>
        val criteria = EndpointMatchCriteria(method, PathMatching((s"^${prefix.value}${suffix.value}$$").r, Nil, prefix, ""))
        makeMatch(method, "/base-path" + prefix.value + suffix.value, BasePath("/base-path"), criteria) must be(Match(PathParams(Map())))
      }
    }

    "return Match with path params" in {
      forAll(minSuccessful(10)) { (method: HttpMethod, prefix: PathPrefix, suffix: PathSuffix) =>
        val criteria = EndpointMatchCriteria(method, PathMatching((s"^${prefix.value}${suffix.value}$$").r, Nil, prefix, ""))
        makeMatch(method, prefix.value + suffix.value, BasePath(""), criteria) must be(Match(PathParams(Map())))
      }
    }

    "return NoMatch when path with prefix IS NOT matching" in {
      val criteria = EndpointMatchCriteria(HttpMethod.GET, PathMatching(("^/prefix/suffix$$").r, Nil, PathPrefix("/prefix"), ""))
      makeMatch(HttpMethod.GET, "/suffix", BasePath(""), criteria) must be(NoMatch)
    }

    "return NoMatch when base-path IS NOT matching" in {
      val criteria = EndpointMatchCriteria(HttpMethod.GET, PathMatching(("^/prefix/suffix$$").r, Nil, PathPrefix("/prefix"), ""))
      makeMatch(HttpMethod.GET, "/other-base-path/prefix/suffix", BasePath("/base-path"), criteria) must be(NoMatch)
    }

    "return NoMatch when path wo prefix IS NOT matching" in {
      val criteria = EndpointMatchCriteria(HttpMethod.GET, PathMatching(("^/suffix$$").r, Nil, PathPrefix(""), ""))
      makeMatch(HttpMethod.GET, "/prefix/suffix", BasePath(""), criteria) must be(NoMatch)
    }

    "return NoMatch when path with prefix IS matching and method IS NOT matching" in {
      val criteria = EndpointMatchCriteria(HttpMethod.GET, PathMatching(("^/prefix/suffix$$").r, Nil, PathPrefix("/prefix"), ""))
      makeMatch(HttpMethod.POST, "/prefix/suffix", BasePath(""), criteria) must be(NoMatch)
    }

    "return NoMatch when path with prefix IS NOT matching and method IS matching" in {
      val criteria = EndpointMatchCriteria(HttpMethod.GET, PathMatching(("^/prefix/suffix$$").r, Nil, PathPrefix("/prefix"), ""))
      makeMatch(HttpMethod.GET, "/suffix", BasePath(""), criteria) must be(NoMatch)
    }
  }
}
