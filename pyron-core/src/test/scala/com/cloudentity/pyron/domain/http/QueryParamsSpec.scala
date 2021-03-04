package com.cloudentity.pyron.domain.http

import scala.util.Success
import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class QueryParamsSpec extends WordSpec with MustMatchers {
  "QueryParams.of" should {
    "construct instance from query string" in {
      QueryParams.fromString("x=1&y=2") mustBe Success(QueryParams(Map("x" -> List("1"), "y" -> List("2"))))
    }

    "construct instance from query string with null values dropped if no name-value pair" in {
      QueryParams.fromString("x") mustBe Success(QueryParams(Map("x" -> List())))
    }

    "construct instance from query string with null values dropped if multiple name-value pair" in {
      QueryParams.fromString("x&x=1") mustBe Success(QueryParams(Map("x" -> List("1"))))
    }

    "construct instance from query string with multiple values per name" in {
      QueryParams.fromString("x=1&x=2") mustBe Success(QueryParams(Map("x" -> List("1", "2"))))
    }
  }

  "QueryParams instance" should {
    val q = QueryParams(Map("x" -> List("1"), "y" -> List("2", "3")))

    "remove entry" in {
      q.remove("x") mustBe QueryParams(Map("y" -> List("2", "3")))
    }

    "remove value and entry if no more values for given name" in {
      q.remove("x", "1") mustBe QueryParams(Map("y" -> List("2", "3")))
    }

    "remove value for given name" in {
      q.remove("y", "2") mustBe QueryParams(Map("x" -> List("1"), "y" -> List("3")))
    }

    "add value for missing name" in {
      q.add("z", "4") mustBe QueryParams(Map("x" -> List("1"), "y" -> List("2", "3"), "z" -> List("4")))
    }

    "add value for existing name" in {
      q.add("x", "4") mustBe QueryParams(Map("x" -> List("1", "4"), "y" -> List("2", "3")))
    }

    "get None if missing name" in {
      q.get("z") mustBe None
    }

    "get first value if multiple values from name" in {
      q.get("y") mustBe Some("2")
    }

    "get all values for names" in {
      q.getValues("y") mustBe Some(List("2", "3"))
    }

    "set value for existing name" in {
      q.set("y", "4") mustBe QueryParams(Map("x" -> List("1"), "y" -> List("4")))
    }

    "return false if does not contain given name" in {
      q.contains("z") mustBe false
    }

    "return true if contains given name" in {
      q.contains("x") mustBe true
    }

    "return false if does not contain given value for given name" in {
      q.contains("x", "4") mustBe false
    }

    "return true if contains given value for given name" in {
      q.contains("x", "1") mustBe true
    }

    "return true if entry exists" in {
      q.exists(_._1 == "x") mustBe true
    }

    "return false if entry does not exist" in {
      q.exists(_._1 == "z") mustBe false
    }
  }
}
