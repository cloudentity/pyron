package com.cloudentity.pyron.apigroup

import io.circe.Json
import com.cloudentity.pyron.apigroup.ApiGroupReader.ApiGroupConfUnresolved
import com.cloudentity.pyron.domain.flow.{BasePath, DomainPattern, GroupMatchCriteria}
import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ApiGroupReaderSpec extends WordSpec with MustMatchers {

  "ApiGroupReader.readApiGroupLevels" should {
    "read simple 0-level with basePath and domains and no rules" in {
      val json = """{
                   |  "_group": {
                   |    "basePath": "/x",
                   |    "domains": [
                   |      "1.com"
                   |    ]
                   |  }
                   |}""".stripMargin

      val group = GroupMatchCriteria(Some(BasePath("/x")), Some(List(DomainPattern("1.com"))))
      ApiGroupReader.readApiGroupLevels(json) must be {
        ValidResult(
          Nil,
          ApiGroupLevel(None, Some(group), Nil)
        )
      }
    }

    "read simple 0-level with basePath and domains and rules" in {
      val json = """{
                   |  "_group": {
                   |    "basePath": "/x",
                   |    "domains": [
                   |      "1.com"
                   |    ]
                   |  },
                   |  "_rules": {}
                   |}""".stripMargin

      val group = GroupMatchCriteria(Some(BasePath("/x")), Some(List(DomainPattern("1.com"))))
      ApiGroupReader.readApiGroupLevels(json) must be {
        ValidResult(
          Nil,
          ApiGroupLevel(Some(Json.obj()), Some(group), Nil)
        )
      }
    }

    "read simple 1st-level with basePath and domains and no rules" in {
      val json = """{
                   |  "service-a": {
                   |    "_group": {
                   |      "basePath": "/x",
                   |      "domains": [
                   |        "1.com"
                   |      ]
                   |    }
                   |  }
                   |}""".stripMargin

      val group = GroupMatchCriteria(Some(BasePath("/x")), Some(List(DomainPattern("1.com"))))
      ApiGroupReader.readApiGroupLevels(json) must be {
        ValidResult(
          Nil,
          ApiGroupLevel(
            None,
            None,
            List(
              ValidResult(
                List("service-a"),
                ApiGroupLevel(None, Some(group), Nil)
              )
            )
          )
        )
      }
    }

    "read simple 1st-level with basePath and domains and rules" in {
      val json = """{
                   |  "service-a": {
                   |    "_group": {
                   |      "basePath": "/x",
                   |      "domains": [
                   |        "1.com"
                   |      ]
                   |    },
                   |    "_rules": {}
                   |  }
                   |}""".stripMargin

      val group = GroupMatchCriteria(Some(BasePath("/x")), Some(List(DomainPattern("1.com"))))
      ApiGroupReader.readApiGroupLevels(json) must be {
        ValidResult(Nil,
          ApiGroupLevel(
            None,
            None,
            List(
              ValidResult(
                List("service-a"),
                ApiGroupLevel(Some(Json.obj()), Some(group), Nil)
              )
            )
          )
        )
      }
    }

    "read simple 2nd-level with basePath and domains and no rules" in {
      val json = """{
                   |  "level-a": {
                   |    "service-a": {
                   |      "_group": {
                   |        "basePath": "/x",
                   |        "domains": [
                   |          "1.com"
                   |        ]
                   |      }
                   |    }
                   |  }
                   |}""".stripMargin

      val group = GroupMatchCriteria(Some(BasePath("/x")), Some(List(DomainPattern("1.com"))))
      ApiGroupReader.readApiGroupLevels(json) must be {
        ValidResult(
          Nil,
          ApiGroupLevel(
            None,
            None,
            List(
              ValidResult(
                List("level-a"),
                ApiGroupLevel(
                  None,
                  None,
                  List(
                    ValidResult(
                      List("service-a", "level-a"),
                      ApiGroupLevel(None, Some(group), Nil)
                    )
                  )
                )
              )
            )
          )
        )
      }
    }

    "read simple 2nd-level with decoding error" in {
      val json = """{
                   |  "level-a": {
                   |    "service-a": {
                   |      "_group": {
                   |        "basePath": []
                   |      }
                   |    }
                   |  }
                   |}""".stripMargin

      val group = GroupMatchCriteria(Some(BasePath("/x")), Some(List(DomainPattern("1.com"))))
      ApiGroupReader.readApiGroupLevels(json) must be {
        ValidResult(
          Nil,
          ApiGroupLevel(
            None,
            None,
            List(
              ValidResult(
                List("level-a"),
                ApiGroupLevel(
                  None,
                  None,
                  List(
                    InvalidResult(
                      List("service-a", "level-a"),
                      "Invalid group level at 'level-a.service-a._group.basePath'"
                    )
                  )
                )
              )
            )
          )
        )
      }
    }

    "read 1st and 2nd-level with basePath and domains and no rules" in {
      val json = """{
                   |  "level-a": {
                   |    "_group": {
                   |      "basePath": "/x",
                   |      "domains": [
                   |        "1.com"
                   |      ]
                   |    },
                   |    "service-a": {
                   |      "_group": {
                   |        "basePath": "/y",
                   |        "domains": [
                   |          "2.com"
                   |        ]
                   |      }
                   |    }
                   |  }
                   |}""".stripMargin

      val group1 = GroupMatchCriteria(Some(BasePath("/x")), Some(List(DomainPattern("1.com"))))
      val group2 = GroupMatchCriteria(Some(BasePath("/y")), Some(List(DomainPattern("2.com"))))
      ApiGroupReader.readApiGroupLevels(json) must be {
        ValidResult(
          Nil,
          ApiGroupLevel(
            None,
            None,
            List(
              ValidResult(
                List("level-a"),
                ApiGroupLevel(
                  None,
                  Some(group1),
                  List(
                    ValidResult(
                      List("service-a", "level-a"),
                      ApiGroupLevel(None, Some(group2), Nil)
                    )
                  )
                )
              )
            )
          )
        )
      }
    }

    "read two 2nd-levels with basePath and domains and no rules with sorting by level-key" in {
      val json = """{
                   |  "level-a": {
                   |    "service-b": {
                   |      "_group": {
                   |        "basePath": "/y",
                   |        "domains": [
                   |          "2.com"
                   |        ]
                   |      }
                   |    },
                   |    "service-a": {
                   |      "_group": {
                   |        "basePath": "/x",
                   |        "domains": [
                   |          "1.com"
                   |        ]
                   |      }
                   |    }
                   |  }
                   |}""".stripMargin

      val group1 = GroupMatchCriteria(Some(BasePath("/x")), Some(List(DomainPattern("1.com"))))
      val group2 = GroupMatchCriteria(Some(BasePath("/y")), Some(List(DomainPattern("2.com"))))
      ApiGroupReader.readApiGroupLevels(json) must be {
        ValidResult(
          Nil,
          ApiGroupLevel(
            None,
            None,
            List(
              ValidResult(
                List("level-a"),
                ApiGroupLevel(
                  None,
                  None,
                  List(
                    ValidResult(
                      List("service-a", "level-a"),
                      ApiGroupLevel(None, Some(group1), Nil)
                    ),
                    ValidResult(
                      List("service-b", "level-a"),
                      ApiGroupLevel(None, Some(group2), Nil)
                    )
                  )
                )
              )
            )
          )
        )
      }
    }
  }

  "ApiGroupReader.buildApiGroupConfsUnresolved" should {
    "build 0-level" in {
      val criteria = GroupMatchCriteria(Some(BasePath("/x")), Some(List(DomainPattern("1.com"))))

      val level =
        ApiGroupLevel(
          Some(Json.arr()),
          Some(criteria),
          Nil
        )

      val group = ApiGroupConfUnresolved(criteria, Json.arr())

      ApiGroupReader.buildApiGroupConfsUnresolved(level)must be (List(ValidResult(Nil, group)))
    }

    "build 1-level with inherited basePath" in {
      val group0 = GroupMatchCriteria(Some(BasePath("/x")), None)
      val group1 = GroupMatchCriteria(Some(BasePath("/y")), None)

      val level =
        ApiGroupLevel(
          None,
          Some(group0),
          List(
            ValidResult(
              List("y"),
              ApiGroupLevel(Some(Json.arr()), Some(group1), Nil)
            )
          )
        )

      val group = ApiGroupConfUnresolved(GroupMatchCriteria(Some(BasePath("/x/y")), None), Json.arr())

      ApiGroupReader.buildApiGroupConfsUnresolved(level) must be (List(ValidResult(List("y"), group)))
    }

    "build 1-level with inherited domains" in {
      val group0 = GroupMatchCriteria(None, Some(List(DomainPattern("*.com"))))
      val group1 = GroupMatchCriteria(Some(BasePath("/x")), None)

      val level =
        ApiGroupLevel(
          None,
          Some(group0),
          List(
            ValidResult(
              List("y"),
              ApiGroupLevel(Some(Json.arr()), Some(group1), Nil)
            )
          )
        )

      val group = ApiGroupConfUnresolved(GroupMatchCriteria(Some(BasePath("/x")), Some(List(DomainPattern("*.com")))), Json.arr())

      ApiGroupReader.buildApiGroupConfsUnresolved(level) must be (List(ValidResult(List("y"), group)))
    }

    "build 1-level with aggregated sub-level failures" in {
      val group0 = GroupMatchCriteria(Some(BasePath("/x")), None)
      val group1a = GroupMatchCriteria(None, None)
      val group1b = GroupMatchCriteria(Some(BasePath("/w")), None)
      val group1c = GroupMatchCriteria(Some(BasePath("/w1")), None)

      val level =
        ApiGroupLevel(
          None,
          Some(group0),
          List(
            ValidResult(
              List("y"),
              ApiGroupLevel(Some(Json.arr(Json.fromString("rules-y"))), Some(group1a), Nil)
            ),
            ValidResult(
              List("z"),
              ApiGroupLevel(Some(Json.arr(Json.fromString("rules-z"))), Some(group1a), Nil)
            ),
            ValidResult(
              List("w"),
              ApiGroupLevel(
                None,
                Some(group1b),
                List(
                  InvalidResult(List("w", "w0"), "invalid"),
                  ValidResult(List("w", "w1"), ApiGroupLevel(Some(Json.arr(Json.fromString("rules-w1"))), Some(group1c), Nil))
                )
              )
            )
          )
        )

      val groupA = ApiGroupConfUnresolved(GroupMatchCriteria(Some(BasePath("/x")), None), Json.arr(Json.fromString("rules-y"), Json.fromString("rules-z")))
      val groupC = ApiGroupConfUnresolved(GroupMatchCriteria(Some(BasePath("/x/w/w1")), None), Json.arr(Json.fromString("rules-w1")))

      ApiGroupReader.buildApiGroupConfsUnresolved(level) must be(List(ValidResult(List(), groupA), ValidResult(List("w", "w1"), groupC), InvalidResult(List("w", "w0"), "invalid")))
    }

    "fail to build 1-level with overwritten domains" in {
      val group0 = GroupMatchCriteria(Some(BasePath("/x")), Some(List(DomainPattern("*.com"))))
      val group1 = GroupMatchCriteria(None, Some(List(DomainPattern("y.com"))))

      val level =
        ApiGroupLevel(
          None,
          Some(group0),
          List(
            ValidResult(
              List("y"),
              ApiGroupLevel(Some(Json.arr()), Some(group1), Nil)
            )
          )
        )

      ApiGroupReader.buildApiGroupConfsUnresolved(level) must be (List(InvalidResult(List("y"), "leaf node with domains set can't inherit them from parent")))
    }

    "fail to build 0-level with both rules and sub-levels set" in {
      val criteria = GroupMatchCriteria(Some(BasePath("/x")), Some(List(DomainPattern("1.com"))))

      val level =
        ApiGroupLevel(
          Some(Json.arr()),
          Some(criteria),
          List(
            ValidResult(
              List("y"),
              ApiGroupLevel(Some(Json.arr()), None, Nil)
            )
          )
        )

      ApiGroupReader.buildApiGroupConfsUnresolved(level)must be (List(InvalidResult(Nil, "leaf node with rules can't have sub-levels")))
    }

    "build 1-level with merged rules" in {
      val group0 = GroupMatchCriteria(Some(BasePath("/x")), None)
      val group1 = GroupMatchCriteria(None, None)

      val level =
        ApiGroupLevel(
          None,
          Some(group0),
          List(
            ValidResult(
              List("y"),
              ApiGroupLevel(Some(Json.arr(Json.fromString("rules-y"))), Some(group1), Nil)
            ),
            ValidResult(
              List("z"),
              ApiGroupLevel(Some(Json.arr(Json.fromString("rules-z"))), Some(group1), Nil)
            )
          )
        )

      val group = ApiGroupConfUnresolved(GroupMatchCriteria(Some(BasePath("/x")), None), Json.arr(Json.fromString("rules-y"), Json.fromString("rules-z")))

      ApiGroupReader.buildApiGroupConfsUnresolved(level) must be(List(ValidResult(List(), group)))
    }

    "build 1-level with/wo merged rules" in {
      val group0 = GroupMatchCriteria(Some(BasePath("/x")), None)
      val group1a = GroupMatchCriteria(None, None)
      val group1b = GroupMatchCriteria(Some(BasePath("/w")), None)

      val level =
        ApiGroupLevel(
          None,
          Some(group0),
          List(
            ValidResult(
              List("y"),
              ApiGroupLevel(Some(Json.arr(Json.fromString("rules-y"))), Some(group1a), Nil)
            ),
            ValidResult(
              List("z"),
              ApiGroupLevel(Some(Json.arr(Json.fromString("rules-z"))), Some(group1a), Nil)
            ),
            ValidResult(
              List("w"),
              ApiGroupLevel(Some(Json.arr(Json.fromString("rules-w"))), Some(group1b), Nil)
            )
          )
        )

      val groupA = ApiGroupConfUnresolved(GroupMatchCriteria(Some(BasePath("/x")), None), Json.arr(Json.fromString("rules-y"), Json.fromString("rules-z")))
      val groupB = ApiGroupConfUnresolved(GroupMatchCriteria(Some(BasePath("/x/w")), None), Json.arr(Json.fromString("rules-w")))

      ApiGroupReader.buildApiGroupConfsUnresolved(level) must be(List(ValidResult(List(), groupA), ValidResult(List("w"), groupB)))
    }
  }
}
