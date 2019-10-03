package com.cloudentity.pyron.apigroup

import com.cloudentity.pyron.domain.flow.{BasePath, DomainPattern, GroupMatchCriteria}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{MustMatchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class ApiGroupConflictsSpec extends WordSpec with MustMatchers {
  "ApiGroupConflicts.isConflicted" should {
    "return true when domains empty and base-path the same" in {
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(None, None), GroupMatchCriteria(None, Some(List(DomainPattern("*.com"))))) must be (true)
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(None, Some(List(DomainPattern("*.com")))), GroupMatchCriteria(None, None)) must be (true)
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(None, None), GroupMatchCriteria(None, None)) must be (true)
    }

    "return true when domains the same and base-path the same" in {
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(None, None), GroupMatchCriteria(None, None)) must be (true)
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(None, Some(List(DomainPattern("*.com")))), GroupMatchCriteria(None, Some(List(DomainPattern("*.com"))))) must be (true)
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(Some(BasePath("/path")), None), GroupMatchCriteria(Some(BasePath("/path")), None)) must be (true)
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(Some(BasePath("/path")), Some(List(DomainPattern("*.com")))), GroupMatchCriteria(Some(BasePath("/path")), Some(List(DomainPattern("*.com"))))) must be (true)
    }

    "return true when domains overlapping and base-path empty" in {
      val emptyBasePath = None

      ApiGroupConflicts.isConflicted(GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("*.com")))), GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("*.com"))))) must be (true)
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("*.com")))), GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("x.com"))))) must be (true)
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("x.com")))), GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("x.com"))))) must be (true)
    }

    "return false when domains overlapping and base-path not prefixed" in {
      val domains1 = Some(List(DomainPattern("*.com")))
      val domains2 = Some(List(DomainPattern("x.com")))

      ApiGroupConflicts.isConflicted(GroupMatchCriteria(Some(BasePath("/a")), domains1), GroupMatchCriteria(Some(BasePath("/b")), domains2)) must be (false)
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(Some(BasePath("/b")), domains1), GroupMatchCriteria(Some(BasePath("/a")), domains2)) must be (false)
    }

    "return false when domains do not overlap and base-path empty" in {
      val emptyBasePath = None

      ApiGroupConflicts.isConflicted(GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("*.com")))), GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("y.x.com"))))) must be (false)
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("*.com")))), GroupMatchCriteria(emptyBasePath, Some(List(DomainPattern("*.org"))))) must be (false)
    }

    "return true when domains empty and base-path prefixed" in {
      val emptyDomains = None

      ApiGroupConflicts.isConflicted(GroupMatchCriteria(Some(BasePath("/x")), emptyDomains), GroupMatchCriteria(Some(BasePath("/x")), emptyDomains)) must be (true)
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(None, emptyDomains), GroupMatchCriteria(Some(BasePath("/x")), emptyDomains)) must be (true)
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(Some(BasePath("/x")), emptyDomains), GroupMatchCriteria(None, emptyDomains)) must be (true)
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(Some(BasePath("/x")), emptyDomains), GroupMatchCriteria(Some(BasePath("/x/y")), emptyDomains)) must be (true)
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(Some(BasePath("/x/y")), emptyDomains), GroupMatchCriteria(Some(BasePath("/x")), emptyDomains)) must be (true)
    }

    "return false when domains empty and base-path not prefixed" in {
      val emptyDomains = None

      ApiGroupConflicts.isConflicted(GroupMatchCriteria(Some(BasePath("/a")), emptyDomains), GroupMatchCriteria(Some(BasePath("/b")), emptyDomains)) must be (false)
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(Some(BasePath("/b")), emptyDomains), GroupMatchCriteria(Some(BasePath("/a")), emptyDomains)) must be (false)
    }

    "return false when domains do not overlap and base-path prefixed" in {
      val domains1 = Some(List(DomainPattern("*.com")))
      val domains2 = Some(List(DomainPattern("*.org")))

      ApiGroupConflicts.isConflicted(GroupMatchCriteria(Some(BasePath("/x")), domains1), GroupMatchCriteria(Some(BasePath("/x")), domains2)) must be (false)
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(None, domains1), GroupMatchCriteria(Some(BasePath("/x")), domains2)) must be (false)
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(Some(BasePath("/x")), domains1), GroupMatchCriteria(None, domains2)) must be (false)
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(Some(BasePath("/x")), domains1), GroupMatchCriteria(Some(BasePath("/x/y")), domains2)) must be (false)
      ApiGroupConflicts.isConflicted(GroupMatchCriteria(Some(BasePath("/x/y")), domains1), GroupMatchCriteria(Some(BasePath("/x")), domains2)) must be (false)
    }
  }
}
