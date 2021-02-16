package com.cloudentity.pyron.apigroup

import com.cloudentity.pyron.apigroup.ApiGroupConflicts.isConflicted
import com.cloudentity.pyron.domain.flow.{BasePath, DomainPattern, GroupMatchCriteria}
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class ApiGroupConflictsSpec extends WordSpec with MustMatchers {
  "ApiGroupConflicts.isConflicted" should {
    "return true when domains empty and base-path the same" in {
      isConflicted(GroupMatchCriteria(None, None), GroupMatchCriteria(None, Some(List(DomainPattern("*.com"))))) mustBe true
      isConflicted(GroupMatchCriteria(None, Some(List(DomainPattern("*.com")))), GroupMatchCriteria(None, None)) mustBe true
      isConflicted(GroupMatchCriteria(None, None), GroupMatchCriteria(None, None)) mustBe true
    }

    "return true when domains the same and base-path the same" in {
      isConflicted(GroupMatchCriteria(None, None), GroupMatchCriteria(None, None)) mustBe true
      isConflicted(GroupMatchCriteria(None, Some(List(DomainPattern("*.com")))), GroupMatchCriteria(None, Some(List(DomainPattern("*.com"))))) mustBe true
      isConflicted(GroupMatchCriteria(Some(BasePath("/path")), None), GroupMatchCriteria(Some(BasePath("/path")), None)) mustBe true
      isConflicted(GroupMatchCriteria(Some(BasePath("/path")), Some(List(DomainPattern("*.com")))), GroupMatchCriteria(Some(BasePath("/path")), Some(List(DomainPattern("*.com"))))) mustBe true
    }

    "return true when domains overlapping and base-path empty" in {
      val emptyBasePath = None

      isConflicted(GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("*.com")))), GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("*.com"))))) mustBe true
      isConflicted(GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("*.com")))), GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("x.com"))))) mustBe true
      isConflicted(GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("x.com")))), GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("x.com"))))) mustBe true
    }

    "return false when domains overlapping and base-path not prefixed" in {
      val domains1 = Some(List(DomainPattern("*.com")))
      val domains2 = Some(List(DomainPattern("x.com")))

      isConflicted(GroupMatchCriteria(Some(BasePath("/a")), domains1), GroupMatchCriteria(Some(BasePath("/b")), domains2)) mustBe false
      isConflicted(GroupMatchCriteria(Some(BasePath("/b")), domains1), GroupMatchCriteria(Some(BasePath("/a")), domains2)) mustBe false
    }

    "return false when domains do not overlap and base-path empty" in {
      val emptyBasePath = None

      isConflicted(GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("*.com")))), GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("y.x.com"))))) mustBe false
      isConflicted(GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("*.com")))), GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("*.org"))))) mustBe false
    }

    "return true when domains empty and base-path prefixed" in {
      val emptyDomains = None

      isConflicted(GroupMatchCriteria(Some(BasePath("/x")), emptyDomains), GroupMatchCriteria(Some(BasePath("/x")), emptyDomains)) mustBe true
      isConflicted(GroupMatchCriteria(None, emptyDomains), GroupMatchCriteria(Some(BasePath("/x")), emptyDomains)) mustBe true
      isConflicted(GroupMatchCriteria(Some(BasePath("/x")), emptyDomains), GroupMatchCriteria(None, emptyDomains)) mustBe true
      isConflicted(GroupMatchCriteria(Some(BasePath("/x")), emptyDomains), GroupMatchCriteria(Some(BasePath("/x/y")), emptyDomains)) mustBe true
      isConflicted(GroupMatchCriteria(Some(BasePath("/x/y")), emptyDomains), GroupMatchCriteria(Some(BasePath("/x")), emptyDomains)) mustBe true
    }

    "return false when domains empty and base-path not prefixed" in {
      val emptyDomains = None

      isConflicted(GroupMatchCriteria(Some(BasePath("/a")), emptyDomains), GroupMatchCriteria(Some(BasePath("/b")), emptyDomains)) mustBe false
      isConflicted(GroupMatchCriteria(Some(BasePath("/b")), emptyDomains), GroupMatchCriteria(Some(BasePath("/a")), emptyDomains)) mustBe false
    }

    "return false when domains do not overlap and base-path prefixed" in {
      val domains1 = Some(List(DomainPattern("*.com")))
      val domains2 = Some(List(DomainPattern("*.org")))

      isConflicted(GroupMatchCriteria(Some(BasePath("/x")), domains1), GroupMatchCriteria(Some(BasePath("/x")), domains2)) mustBe false
      isConflicted(GroupMatchCriteria(None, domains1), GroupMatchCriteria(Some(BasePath("/x")), domains2)) mustBe false
      isConflicted(GroupMatchCriteria(Some(BasePath("/x")), domains1), GroupMatchCriteria(None, domains2)) mustBe false
      isConflicted(GroupMatchCriteria(Some(BasePath("/x")), domains1), GroupMatchCriteria(Some(BasePath("/x/y")), domains2)) mustBe false
      isConflicted(GroupMatchCriteria(Some(BasePath("/x/y")), domains1), GroupMatchCriteria(Some(BasePath("/x")), domains2)) mustBe false
    }
  }
}
