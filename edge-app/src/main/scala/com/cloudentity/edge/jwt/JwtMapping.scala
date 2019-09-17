package com.cloudentity.edge.jwt

import io.circe.{Json, JsonObject}

object JwtMapping {

  /**
    * For json
    *  {
    *    "usr": "${user}",
    *    "app": "${apps.1}",
    *    "session": "${content.session}",
    *    "level": "${auth.level},
    *    "sso": {
    *      "active": "${session.active}"
    *    }
    *  }
    *  and refs
    *  {
    *    "token": "1234",
    *    "session": {"uuid": "a", "deviceUuid": "b", "active": true},
    *    "authnMethod": "sso",
    *    "apps": [{"id": "app1"}]
    *    "user": {"uuid": "a", "name": "andrzej"},
    *    "device": {"uuid": "b", "name": "android"}
    *  }
    *  and defaults
    *  {
    *    "auth.level": 15
    *  }
    *  it produces
    *  {
    *    "sso" : {
    *      "active" : true
    *    },
    *    "app": {"id": "app1"},
    *    "level": 15,
    *    "usr" : {
    *      "uuid" : "a",
    *      "name" : "andrzej"
    *    }
    *  }
    */
  def updateWithRefs(json: Json, refs: Json, defaults: JsonObject = JsonObject.empty): Json = {
    val pattern = """\$\{(.+)\}""".r

    def pre(prefix: String) = if (prefix == "") "" else s"$prefix."

    def indexJson(prefix: String, value: Json): Map[String, Json] = {
      value.fold[Map[String, Json]](
        Map(),
        v => Map(prefix -> Json.fromBoolean(v)),
        v => Map(prefix -> Json.fromJsonNumber(v)),
        v => Map(prefix -> Json.fromString(v)),
        v => v.zipWithIndex.map {
          case (innerJson, key) =>
            indexJson(s"${pre(prefix)}${key + 1}", innerJson)
        }.fold(Map(prefix -> Json.fromValues(v)))(_ ++ _),
        v => v.toList.map {
          case (key, innerJson) =>
            indexJson(s"${pre(prefix)}$key", innerJson)
        }.fold(Map(prefix -> Json.fromJsonObject(v)))(_ ++ _)
      )
    }

    def update(json: Json, refs: Map[String, Json]): Json = {
      json.fold(
        Json.Null,
        v => Json.fromBoolean(v),
        v => Json.fromJsonNumber(v),
        v => Json.fromString(v),
        v => Json.fromValues(v.map(js => update(js, refs))),
        _.toList.map {
          case (key, value) =>
            value.asString match {
              case Some(pattern(refKey)) =>
                refs.get(refKey)
                  .map(v => Json.obj(key -> v))
                  .orElse(defaults(refKey).map(v => Json.obj(key -> v)))
                  .getOrElse(Json.obj())
              case Some(str) =>
                Json.obj(key -> Json.fromString(str))
              case _ =>
                Json.obj(key -> update(value, refs))
            }
        }.fold(Json.obj())(_.deepMerge(_))
      )
    }
    update(json, indexJson("", refs))
  }
}
