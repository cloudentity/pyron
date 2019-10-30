## Plugins in routing rule

Plugin configuration has two attributes `name` and `conf`:

```json
{
  "name": "plugin-a",
  "conf": {
    "param-name": "param-value"
  }
}
```

> NOTE<br/>
> `conf` attribute may not be required - it depends on plugin implementation.

Let's apply `plugin-a` to a request by defining it in `requestPlugins` attribute:

```json
{
  "rules": [
    {
      "default": {
        "targetHost": "example.com",
        "targetPort": 80
      },
      "endpoints": [
        {
          "method": "POST",
          "pathPattern": "/user",
          "requestPlugins": [
            {
              "name": "plugin-a",
              "conf": {
                "param-name": "param-value"
              }
            }
          ]
        }
      ]
    }
  ]
}
```

> NOTE<br/>
> Plugins are configured in an array and are applied sequentially.

If you want to apply a plugin to a response, then configure it in `responsePlugins` attribute:

```json
{
  "rules": [
    {
      "default": {
        "targetHost": "example.com",
        "targetPort": 80
      },
      "endpoints": [
        {
          "method": "POST",
          "pathPattern": "/user",
          "responsePlugins": [
            {
              "name": "plugin-x"
            }
          ]
        }
      ]
    }
  ]
}
```

### Pre/post flow plugins

It is common case to apply a plugin for all endpoints (e.g. authentication) and then apply specific plugins per endpoint.

Plugins applied to a request are defined in following attributes:

* `request.preFlow.plugins`
* `endpoints[].requestPlugins`
* `request.postFlow.plugins`

Similarly, for a response we have:

* `response.preFlow.plugins`
* `endpoints[].responsePlugins`
* `response.postFlow.plugins`

If you want to apply a plugin before endpoint plugins then configure it in `request.preFlow.plugins` attribute:

```json
{
  "rules": [
    {
      "default": {
        "targetHost": "example.com",
        "targetPort": 80
      },
      "request": {
        "preFlow": {
          "plugins": [
            {
              "name": "plugin-a",
              "conf": {
                "param-name": "param-value"
              }
            }
          ]
        }
      },
      "endpoints": [
        {
          "method": "POST",
          "pathPattern": "/user",
          "plugins": [
            {
              "name": "plugin-x"
            }
          ]
        }
      ]
    }
  ]
}
```

In the example above, `plugin-a` is applied for all endpoints in `preFlow` phase.
In case of `/user` endpoint, first `plugin-a` is applied and then `plugin-x`.

If you want to apply a plugin after endpoint plugins then configure it in `request.postFlow.plugins` attribute:

```json
{
  "rules": [
    {
      "default": {
        "targetHost": "example.com",
        "targetPort": 80
      },
      "request": {
        "postFlow": {
          "plugins": [
            {
              "name": "plugin-a",
              "conf": {
                "param-name": "param-value"
              }
            }
          ]
        }
      },
      "endpoints": [
        {
          "method": "POST",
          "pathPattern": "/user",
          "plugins": [
            {
              "name": "plugin-x"
            }
          ]
        }
      ]
    }
  ]
}
```

In the example above, `plugin-c` is applied for all endpoints in `postFlow` phase.
In case of `/user` endpoint, first `plugin-x` is applied and then `plugin-a`.

Similarly, you can define `preFlow` and `postFlow` plugins for response of all endpoints. Define `response` attribute like below:

```json
{
  "rules": [
    {
      "default": {
        "targetHost": "example.com",
        "targetPort": 80
      },
      "response": {
        "preFlow": {
          "plugins": [
            {
              "name": "plugin-x"
            }
          ]
        },
        "postFlow": {
          "plugins": [
            {
              "name": "plugin-y"
            }
          ]
        }
      },
      "endpoints": [
        {
          "method": "POST",
          "pathPattern": "/user"
        }
      ]
    }
  ]
}
```

The following flow diagram shows what is the order of plugins application:

![][flow]

This configuration snippet defines `preFlow`, `endpoint` and `postFlow` plugins:

```json
{
  "rules": [
    {
      "default": {
        "targetHost": "example.com",
        "targetPort": 80
      },
      "request": {
        "preFlow": {
          "plugins": [
            {
              "name": "req-pre-1"
            },
            {
              "name": "req-pre-2"
            }
          ]
        },
        "postFlow": {
          "plugins": [
            {
              "name": "req-post-1"
            }
          ]
        }
      },
      "response": {
        "preFlow": {
          "plugins": [
            {
              "name": "resp-pre-1"
            }
          ]
        },
        "postFlow": {
          "plugins": [
            {
              "name": "resp-post-1"
            }
          ]
        }
      },
      "endpoints": [
        {
          "method": "POST",
          "pathPattern": "/user",
          "requestPlugins": [
            {
              "name": "req-end-1"
            }
          ],
          "responsePlugins": [
            {
              "name": "resp-end-1"
            }
          ]
        }
      ]
    }
  ]
}
```

Following diagram shows the order in which plugins are applied:

![][plugins-example]

#### Disabling preFlow/postFlow plugins

It is possible to disable `preFlow` or `postFlow` plugins for specific endpoint.
In order to disable all `preFlow` or `postFlow` plugins you should set `disableAllPlugins` to true.

See an example, only `plugin-a` and `plugin-x` plugins are applied:

```
...
"request": {
  "preFlow": {
    "plugins": [
      {
        "name": "plugin-a"
      }
    ]
  },
  "postFlow": {
    "plugins": [
      {
        "name": "plugin-b"
      }
    ]
  }
},
"endpoints": [
  {
    "method": "POST",
    "pathPattern": "/user",
    "request": {
      "postFlow": {
        "disableAllPlugins": true
      }
    }
    "requestPlugins": [
      {
        "name": "plugin-x"
      }
    ]
  }
]
...
```

In order to disable only some `preFlow` or `postFlow` plugins, we can put their names in `disablePlugins` attribute.

Let's disable `plugin-b` `postFlow` plugin:

```
...
"request": {
  "preFlow": {
    "plugins": [
      {
        "name": "plugin-a"
      }
    ]
  },
  "postFlow": {
    "plugins": [
      {
        "name": "plugin-b"
      }
    ]
  }
},
"endpoints": [
  {
    "method": "POST",
    "pathPattern": "/user",
    "request": {
      "postFlow": {
        "disablePlugins": ["plugin-b"]
      }
    }
    "requestPlugins": [
      {
        "name": "plugin-x"
      }
    ]
  }
]
...
```

[flow]: flow.png
[plugins-example]: plugins-example.png