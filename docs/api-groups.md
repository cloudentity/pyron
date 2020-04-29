## API Groups configuration

API Groups configuration has hierarchical structure allowing for flexible grouping of rule sets and plugins.
Configuration node structure is dynamic. Think of it as of a tree. Each node, whether it's internal or leaf, can store grouping config.
Additionally, a leaf node must store routing rules.

Domains/base-path pairs are unique across all API Groups, so there is no risk of request match conflicts between different rule sets.

* [Single API Group](#single)
* [Multiple API Groups](#multiple-api-groups)
* [Multiple sets of rules](#multiple-rule-sets)
* [Nested API Groups](#nested)
* [Domains and base paths](#domains-paths)
* [API Group plugins](#plugins)

<a id="single"></a>
### Single API Group

Let's have a look at simple API Group that exposes rules on `demo.com` domain and `/apis` base-path:

```json
{
  "apiGroups": {
    "single-group": {
      "_group": {
        "domains": ["demo.com"],
        "basePath": "/apis"
      },
      "_rules": [
        {
          "default": {
            "targetHost": "a.service.com",
            "targetPort": 8080
          },
          "endpoints": [
            {
              "method": "GET",
              "pathPattern": "/user"
            }
          ]
        }
      ]
    }
  }
}
```

In our example `apiGroups.single-group` is a leaf node and stores grouping config with rules at `_group` and `_rules` keys respectively.

<a id="multiple-api-groups"></a>
### Multiple API Groups

In following example we have 2 API Groups exposed on `demo.com` domain, but on different base-paths: `/service-a` and `/service-b`.

For simplicity, we use references to define `_rules`.

```json
{
  "apiGroups": {
    "demo": {
      "_group": {
        "domains": ["demo.com"]
      },
      "service-a": {
        "_group": {
          "basePath": "/service-a"
        },
        "_rules": "$ref:some-rules"
      },
      "service-b": {
        "_group": {
          "basePath": "/service-b"
        },
        "_rules": "$ref:some-other-rules"
      }

    }
  }
}
```

<a id="multiple-rule-sets"></a>
### Multiple sets of rules

In this example we have a single API Group configured at `apiGroups.demo` path with 2 sub-nodes containing rule sets.

```json
{
  "apiGroups": {
    "demo": {
      "_group": {
        "domains": ["demo.com"],
        "basePath": "/apis"
      },
      "service-a": {
        "_rules": "$ref:some-rules"
      },
      "service-b": {
        "_rules": "$ref:some-other-rules"
      }
    }
  }
}
```

The rules from sub-nodes are added to the group in alphabetical order of sub-node key.
In our example, `service-a` rules would be added before `service-b`.

<a id="nested"></a>
### Nested API Groups

Consider situation when you have 2 sets of rules. You want to expose them on `/apis/service-a` and `/apis/service-b` paths.
You can either use approach described in [Multiple API Groups config](#multiple-api-groups) or use groups nesting:

```json
{
  "apiGroups": {
    "demo": {
      "_group": {
        "basePath": "/apis"
      },
      "some-grouping": {
        "service-a": {
          "_group": {
            "basePath": "/service-a"
          },
          "_rules": "$ref:some-rules"
        },
        "service-b": {
          "_group": {
            "basePath": "/service-b"
          },
          "_rules": "$ref:some-other-rules"
        }
      }
    }
  }
}
```

In this example, we have 2 leaf nodes:

```json
{
  "service-a": {
    "_group": {
      "basePath": "/service-a"
    },
    "_rules": "$ref:some-rules"
  }
}
```

and

```json
{
  "service-b": {
    "_group": {
      "basePath": "/service-b"
    },
    "_rules": "$ref:some-other-rules"
  }
}
```

The API Groups are constructed by going from leaves up the configuration hierarchy and concatenating base-paths along the way.
In our case, starting from `service-a` node, we would go up and collect `/apis` base-path from `apiGroups.demo` node and then reach the configuration root.
In result, the concatenated base-path for `service-a` group is `/apis/service-a`. Similarly, for `service-b` we get `/apis/service-b`.

Note that `domains` can be defined at most once in the API Group configuration hierarchy.

<a id="domains-paths"></a>
### Domains and base paths

Let's assume we have following API Groups:

| path           | domains                                  | basePath         |
| ---------------|------------------------------------------|------------------|
| demo.service-a | ["demo.com"]                             | /apis/service-a  |
| demo.service-b | ["demo.com"]                             | /apis/service-b  |
| cloudentity    | ["cloudentity.com", "*.cloudentity.com"] | N/A

#### Request matching

Given `GET demo.com/apis/service-b/list` request, first domain is matched. We have `demo.service-a` and `demo.service-b` groups matching the domain.
Then the request path is checked whether it starts with any of the base-paths. In this case, the request would match `demo.service-b` API Group.
Finally the request with dropped base-path (i.e. `GET demo.com/list`) would be matched against `demo.service-b` rules.

If `domains` is not set then the API Group matches all domains.

`domains` attribute supports wildcard `*`.
The wildcard matches single domain part in the prefix, e.g. `*.baz` matches `foo.baz`, but not `foo.bar.baz`, etc.

Given `GET cloudentity.com/api` or `GET demo.cloudentity.com/api` requests, we would match `cloudentity` API Group. Note that the `basePath` may be empty and thus match all paths.

#### Domain and base path conflicts

It is forbidden to have two API Groups with overlapping `domains` and `basePath` at the same time.

Two domain arrays overlap when at least one domain from first array overlaps a domain from another array.

Two domains overlap when:

* they are equal
* they match with wildcard `*`

E.g.:

* `*.cloudentity.com` overlaps `demo.cloudentity.com`, but does not overlap `app.demo.cloudentity.com`

Two base-paths overlap when:

* one is a prefix of another

E.g.:

* `/apis` overlaps `/apis/service-a`
* empty base-path overlaps any other base-path

Given following API Groups:


| path           | domains                                  | basePath         |
| ---------------|------------------------------------------|------------------|
| demo.service-a | ["demo.com"]                             | /apis/service-a  |
| demo.service-b | ["demo.com"]                             | /apis/service-b  |
| cloudentity    | ["cloudentity.com", "*.cloudentity.com"] | N/A              |

API Group `{domains=["cloudentity.com"], basePath="/session"}` is in conflict with `cloudentity` API Group, because empty base-path overlaps `/session` and at the same time their domains overlap.

However, API Group `{domains=["a.demo.com"], basePath="/apis/service-a"}` is not in conflict. Its base-path overlaps the one of `demo.service-a`, but their domains do not overlap.

<a id="plugins"></a>
### API Group plugins

When an API Group needs to use its own plugin instance it can be deployed using `_plugins` array attribute.

Plugin instance entry attributes:

| Name    | Description                                                                  |
|:--------|:-----------------------------------------------------------------------------|
| plugin  | name of the plugin in API Group rules                                        |
| module  | name of the plugin module, see plugin documentation                          |
| env     | overwrite environment variables used by the plugin, see plugin documentation |

With below configuration, rules in `service-a` API group use `cors` plugin with `PLUGIN_CORS__ALLOW_CREDENTIALS` set to `true`,
while rules in `service-b` API group use `cors` plugin with `PLUGIN_CORS__ALLOW_CREDENTIALS` set to `false`.

```json
{
  "apiGroups": {
    "service-a": {
      "_group": {
        "basePath": "/service-a"
      },
      "_rules": "$ref:some-rules",
      "_plugins": [
        {
          "plugin": "cors",
          "module": "plugin/cors",
          "env": {
            "PLUGIN_CORS__ALLOW_CREDENTIALS": "true"
          }
        }
      ]
    },
    "service-b": {
      "_group": {
        "basePath": "/service-b"
      },
      "_rules": "$ref:some-other-rules",
      "_plugins": [
        {
          "plugin": "cors",
          "module": "plugin/cors",
           "env": {
             "PLUGIN_CORS__ALLOW_CREDENTIALS": "false"
           }
        }
      ]
    }
  }
}
```

#### Sharing plugins between API groups

API group plugins can be shared if they are defined in common ancestor.

Given following configuration, both `service-a` and `service-b` can use the same `cors` plugin.

```json
{
  "apiGroups": {
    "_plugins": [
      {
        "plugin": "cors",
        "module": "plugin/cors",
        "env": {
          "PLUGIN_CORS__ALLOW_CREDENTIALS": "true"
        }
      }
    ],
    "service-a": {
      "_group": {
        "basePath": "/service-a"
      },
      "_rules": "$ref:some-rules"
    },
    "service-b": {
      "_group": {
        "basePath": "/service-b"
      },
      "_rules": "$ref:some-other-rules"
    }
  }
}
```

An API group can overwrite inherited plugin.

Given following configuration, `service-a` uses `cors` plugin with `PLUGIN_CORS__ALLOW_CREDENTIALS` set to `true`,
while `service-b` uses `cors` plugin with `PLUGIN_CORS__ALLOW_CREDENTIALS` set to `false`.

```json
{
  "apiGroups": {
    "_plugins": [
      {
        "plugin": "cors",
        "module": "plugin/cors",
        "env": {
          "PLUGIN_CORS__ALLOW_CREDENTIALS": "true"
        }
      }
    ],
    "service-a": {
      "_group": {
        "basePath": "/service-a"
      },
      "_rules": "$ref:some-rules"
    },
    "service-b": {
      "_group": {
        "basePath": "/service-b"
      },
      "_rules": "$ref:some-other-rules",
      "_plugins": [
        {
          "plugin": "cors",
          "module": "plugin/cors",
          "env": {
            "PLUGIN_CORS__ALLOW_CREDENTIALS": "false"
          }
        }
      ]
    }
  }
}
```