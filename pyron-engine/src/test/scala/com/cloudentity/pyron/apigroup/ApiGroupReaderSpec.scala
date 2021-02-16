package com.cloudentity.pyron.apigroup

import io.circe.Json
import com.cloudentity.pyron.domain.flow.{BasePath, DomainPattern, GroupMatchCriteria, PluginName}
import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatestplus.junit.JUnitRunner

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
      ApiGroupReader.readApiGroupLevels(json) mustBe {
        ValidResult(
          Nil,
          ApiGroupLevel(rules = None, group = Some(group), plugins = Nil, subs = Nil)
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
      ApiGroupReader.readApiGroupLevels(json) mustBe {
        ValidResult(
          Nil,
          ApiGroupLevel(rules = Some(Json.obj()), group = Some(group), plugins = Nil, subs = Nil)
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
      ApiGroupReader.readApiGroupLevels(json) mustBe {
        ValidResult(
          path = Nil,
          value = ApiGroupLevel(
            rules = None,
            group = None,
            plugins = Nil,
            subs = List(
              ValidResult(
                List("service-a"),
                ApiGroupLevel(None, Some(group), Nil, Nil)
              )
            )
          )
        )
      }
    }

    "read simple 1st-level with basePath and domains and no rules and plugins" in {
      val json = """{
                   |  "service-a": {
                   |    "_group": {
                   |      "basePath": "/x",
                   |      "domains": [
                   |        "1.com"
                   |      ]
                   |    },
                   |    "_plugins": [
                   |      {
                   |        "plugin": "authn",
                   |        "id": "service-a"
                   |      }
                   |    ]
                   |  }
                   |}""".stripMargin

      val group = GroupMatchCriteria(Some(BasePath("/x")), Some(List(DomainPattern("1.com"))))
      ApiGroupReader.readApiGroupLevels(json) mustBe {
        ValidResult(
          path = Nil,
          value = ApiGroupLevel(
            rules = None,
            group = None,
            plugins = Nil,
            subs = List(
              ValidResult(
                path = List("service-a"),
                value = ApiGroupLevel(None, Some(group), List(ApiGroupPlugin(PluginName("authn"), PluginId("service-a"))), Nil)
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
      ApiGroupReader.readApiGroupLevels(json) mustBe {
        ValidResult(
          path = Nil,
          value = ApiGroupLevel(
            rules = None,
            group = None,
            plugins = Nil,
            subs = List(
              ValidResult(
                path = List("service-a"),
                value = ApiGroupLevel(Some(Json.obj()), Some(group), Nil, Nil)
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
      ApiGroupReader.readApiGroupLevels(json) mustBe {
        ValidResult(
          path = Nil,
          value = ApiGroupLevel(
            rules = None,
            group = None,
            plugins = Nil,
            subs = List(
              ValidResult(
                path = List("level-a"),
                value = ApiGroupLevel(
                  rules = None,
                  group = None,
                  plugins = Nil,
                  subs = List(
                    ValidResult(
                      path = List("service-a", "level-a"),
                      value = ApiGroupLevel(None, Some(group), Nil, Nil)
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

      ApiGroupReader.readApiGroupLevels(json) mustBe {
        ValidResult(
          path = Nil,
          value = ApiGroupLevel(
            rules = None,
            group = None,
            plugins = Nil,
            subs = List(
              ValidResult(
                path = List("level-a"),
                value = ApiGroupLevel(
                  rules = None,
                  group = None,
                  plugins = Nil,
                  subs = List(
                    InvalidResult(
                      path = List("service-a", "level-a"),
                      msg = "Invalid group level at 'level-a.service-a._group.basePath'"
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
      ApiGroupReader.readApiGroupLevels(json) mustBe {
        ValidResult(
          path = Nil,
          value = ApiGroupLevel(
            rules = None,
            group = None,
            plugins = Nil,
            subs = List(
              ValidResult(
                path = List("level-a"),
                value = ApiGroupLevel(
                  rules = None,
                  group = Some(group1),
                  plugins = Nil,
                  subs = List(
                    ValidResult(
                      path = List("service-a", "level-a"),
                      value = ApiGroupLevel(None, Some(group2), Nil, Nil)
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
      ApiGroupReader.readApiGroupLevels(json) mustBe {
        ValidResult(
          path = Nil,
          value = ApiGroupLevel(
            rules = None,
            group = None,
            plugins = Nil,
            subs = List(
              ValidResult(
                path = List("level-a"),
                value = ApiGroupLevel(
                  rules = None,
                  group = None,
                  plugins = Nil,
                  subs = List(
                    ValidResult(
                      path = List("service-a", "level-a"),
                      value = ApiGroupLevel(None, Some(group1), Nil, Nil)
                    ),
                    ValidResult(
                      path = List("service-b", "level-a"),
                      value = ApiGroupLevel(None, Some(group2), Nil, Nil)
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

  "read two 2nd-levels with basePath and domains and no rules and plugins with sorting by level-key" in {
    val json = """{
                 |  "level-a": {
                 |    "service-b": {
                 |      "_group": {
                 |        "basePath": "/y",
                 |        "domains": [
                 |          "2.com"
                 |        ]
                 |      },
                 |      "_plugins": [
                 |        {
                 |          "plugin": "authn",
                 |          "id": "level-a-service-b"
                 |        }
                 |      ]
                 |    },
                 |    "service-a": {
                 |      "_group": {
                 |        "basePath": "/x",
                 |        "domains": [
                 |          "1.com"
                 |        ]
                 |      },
                 |      "_plugins": [
                 |        {
                 |          "plugin": "authn",
                 |          "id": "level-a-service-a"
                 |        }
                 |      ]
                 |    }
                 |  }
                 |}""".stripMargin

    val group1 = GroupMatchCriteria(Some(BasePath("/x")), Some(List(DomainPattern("1.com"))))
    val group2 = GroupMatchCriteria(Some(BasePath("/y")), Some(List(DomainPattern("2.com"))))
    ApiGroupReader.readApiGroupLevels(json) mustBe {
      ValidResult(
        path = Nil,
        value = ApiGroupLevel(
          rules = None,
          group = None,
          plugins = Nil,
          subs = List(
            ValidResult(
              path = List("level-a"),
              value = ApiGroupLevel(
                rules = None,
                group = None,
                plugins = Nil,
                subs = List(
                  ValidResult(
                    path = List("service-a", "level-a"),
                    value = ApiGroupLevel(
                      rules = None,
                      group = Some(group1),
                      plugins = List(ApiGroupPlugin(PluginName("authn"), PluginId("level-a-service-a"))),
                      subs = Nil
                    )
                  ),
                  ValidResult(
                    path = List("service-b", "level-a"),
                    value = ApiGroupLevel(
                      rules = None,
                      group = Some(group2),
                      plugins = List(ApiGroupPlugin(PluginName("authn"), PluginId("level-a-service-b"))),
                      subs = Nil
                    )
                  )
                )
              )
            )
          )
        )
      )
    }
  }

  "ApiGroupReader.buildApiGroupConfsUnresolved" should {
    "build 0-level" in {
      val criteria = GroupMatchCriteria(Some(BasePath("/x")), Some(List(DomainPattern("1.com"))))

      val level = ApiGroupLevel(
        rules = Some(Json.arr()),
        group = Some(criteria),
        plugins = Nil,
        subs = Nil
      )

      val group = ApiGroupConfUnresolved(matchCriteria = criteria, rules = Json.arr(), plugins = Nil)

      ApiGroupReader.buildApiGroupConfsUnresolved(level) mustBe List(ValidResult(path = Nil, value = group))
    }

    "build 1-level with inherited basePath" in {
      val group0 = GroupMatchCriteria(Some(BasePath("/x")), None)
      val group1 = GroupMatchCriteria(Some(BasePath("/y")), None)

      val level = ApiGroupLevel(
        rules = None,
        group = Some(group0),
        plugins = Nil,
        subs = List(
          ValidResult(
            path = List("y"),
            value = ApiGroupLevel(Some(Json.arr()), Some(group1), Nil, Nil)
          )
        )
      )

      val group = ApiGroupConfUnresolved(GroupMatchCriteria(Some(BasePath("/x/y")), None), Json.arr(), Nil)

      ApiGroupReader.buildApiGroupConfsUnresolved(level) mustBe List(ValidResult(List("y"), group))
    }

    "build 1-level with inherited domains" in {
      val group0 = GroupMatchCriteria(None, Some(List(DomainPattern("*.com"))))
      val group1 = GroupMatchCriteria(Some(BasePath("/x")), None)

      val level = ApiGroupLevel(
        rules = None,
        group = Some(group0),
        plugins = Nil,
        subs = List(
          ValidResult(
            path = List("y"),
            value = ApiGroupLevel(Some(Json.arr()), Some(group1), Nil, Nil)
          )
        )
      )

      val group = ApiGroupConfUnresolved(
        matchCriteria = GroupMatchCriteria(
          basePath = Some(BasePath("/x")),
          domains = Some(List(DomainPattern("*.com")))
        ),
        rules = Json.arr(),
        plugins = Nil
      )

      ApiGroupReader.buildApiGroupConfsUnresolved(level) mustBe List(ValidResult(List("y"), group))
    }

    "build 1-level with aggregated sub-level failures" in {
      val group0 = GroupMatchCriteria(Some(BasePath("/x")), None)
      val group1a = GroupMatchCriteria(None, None)
      val group1b = GroupMatchCriteria(Some(BasePath("/w")), None)
      val group1c = GroupMatchCriteria(Some(BasePath("/w1")), None)

      val level = ApiGroupLevel(
        rules = None,
        group = Some(group0),
        plugins = Nil,
        subs = List(
          ValidResult(
            path = List("y"),
            value = ApiGroupLevel(Some(Json.arr(Json.fromString("rules-y"))), Some(group1a), Nil, Nil)
          ),
          ValidResult(
            path = List("z"),
            value = ApiGroupLevel(Some(Json.arr(Json.fromString("rules-z"))), Some(group1a), Nil, Nil)
          ),
          ValidResult(
            path = List("w"),
            value = ApiGroupLevel(
              rules = None,
              group = Some(group1b),
              plugins = Nil,
              subs = List(
                InvalidResult(path = List("w", "w0"), msg = "invalid"),
                ValidResult(
                  path = List("w", "w1"),
                  value = ApiGroupLevel(
                    rules = Some(Json.arr(Json.fromString("rules-w1"))),
                    group = Some(group1c),
                    plugins = Nil,
                    subs = Nil
                  )
                )
              )
            )
          )
        )
      )

      val groupA = ApiGroupConfUnresolved(
        matchCriteria = GroupMatchCriteria(Some(BasePath("/x")), None),
        rules = Json.arr(Json.fromString("rules-y"), Json.fromString("rules-z")),
        plugins = Nil
      )
      val groupC = ApiGroupConfUnresolved(
        matchCriteria = GroupMatchCriteria(Some(BasePath("/x/w/w1")), None),
        rules = Json.arr(Json.fromString("rules-w1")),
        plugins = Nil
      )

      ApiGroupReader.buildApiGroupConfsUnresolved(level) mustBe List(
        ValidResult(List(), groupA),
        ValidResult(List("w", "w1"), groupC),
        InvalidResult(List("w", "w0"), "invalid")
      )
    }

    "fail to build 1-level with overwritten domains" in {
      val group0 = GroupMatchCriteria(Some(BasePath("/x")), Some(List(DomainPattern("*.com"))))
      val group1 = GroupMatchCriteria(None, Some(List(DomainPattern("y.com"))))

      val level = ApiGroupLevel(
        rules = None,
        group = Some(group0),
        plugins = Nil,
        subs = List(
          ValidResult(
            path = List("y"),
            value = ApiGroupLevel(Some(Json.arr()), Some(group1), Nil, Nil)
          )
        )
      )

      ApiGroupReader.buildApiGroupConfsUnresolved(level) mustBe List(
        InvalidResult(List("y"), "leaf node with domains set can't inherit them from parent")
      )
    }

    "fail to build 0-level with both rules and sub-levels set" in {
      val criteria = GroupMatchCriteria(Some(BasePath("/x")), Some(List(DomainPattern("1.com"))))

      val level = ApiGroupLevel(
        rules = Some(Json.arr()),
        group = Some(criteria),
        plugins = Nil,
        subs = List(
          ValidResult(
            path = List("y"),
            value = ApiGroupLevel(Some(Json.arr()), None, Nil, Nil)
          )
        )
      )

      ApiGroupReader.buildApiGroupConfsUnresolved(level) mustBe List(InvalidResult(Nil, "leaf node with rules can't have sub-levels"))
    }

    "build 1-level with merged rules" in {
      val group0 = GroupMatchCriteria(Some(BasePath("/x")), None)
      val group1 = GroupMatchCriteria(None, None)

      val level = ApiGroupLevel(
        rules = None,
        group = Some(group0),
        plugins = Nil,
        subs = List(
          ValidResult(
            path = List("y"),
            value = ApiGroupLevel(Some(Json.arr(Json.fromString("rules-y"))), Some(group1), Nil, Nil)
          ),
          ValidResult(
            path = List("z"),
            value = ApiGroupLevel(Some(Json.arr(Json.fromString("rules-z"))), Some(group1), Nil, Nil)
          )
        )
      )

      val group = ApiGroupConfUnresolved(
        matchCriteria = GroupMatchCriteria(Some(BasePath("/x")), None),
        rules = Json.arr(Json.fromString("rules-y"), Json.fromString("rules-z")),
        plugins = Nil
      )

      ApiGroupReader.buildApiGroupConfsUnresolved(level) mustBe List(ValidResult(List(), group))
    }

    "build 1-level with/wo merged rules" in {
      val group0 = GroupMatchCriteria(Some(BasePath("/x")), None)
      val group1a = GroupMatchCriteria(None, None)
      val group1b = GroupMatchCriteria(Some(BasePath("/w")), None)

      val level = ApiGroupLevel(
        rules = None,
        group = Some(group0),
        plugins = Nil,
        subs = List(
          ValidResult(
            path = List("y"),
            value = ApiGroupLevel(Some(Json.arr(Json.fromString("rules-y"))), Some(group1a), Nil, Nil)
          ),
          ValidResult(
            path = List("z"),
            value = ApiGroupLevel(Some(Json.arr(Json.fromString("rules-z"))), Some(group1a), Nil, Nil)
          ),
          ValidResult(
            path = List("w"),
            value = ApiGroupLevel(Some(Json.arr(Json.fromString("rules-w"))), Some(group1b), Nil, Nil)
          )
        )
      )

      val groupA = ApiGroupConfUnresolved(
        matchCriteria = GroupMatchCriteria(Some(BasePath("/x")), None),
        rules = Json.arr(Json.fromString("rules-y"), Json.fromString("rules-z")),
        plugins = Nil
      )
      val groupB = ApiGroupConfUnresolved(
        matchCriteria = GroupMatchCriteria(Some(BasePath("/x/w")), None),
        rules = Json.arr(Json.fromString("rules-w")),
        plugins = Nil
      )

      ApiGroupReader.buildApiGroupConfsUnresolved(level) mustBe List(ValidResult(List(), groupA), ValidResult(List("w"), groupB))
    }
  }
}
