## Request transformation plugin

`transform-request` plugin performs request transformations (e.g. setting header values, JSON body attributes, path parameters etc.).

Enable `transform-request` plugin by adding `plugin/transform-request` to `MODULES` environment variable.

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
          "pathPattern": "/user/{id}",
          "rewritePath": "/user",
          "requestPlugins": [
            {
              "name": "transform-request",
              "conf": {
                "headers": {
                  "set": {
                    "X-USER-ID": "$pathParams.id"
                  }
                },
                "body": {
                  "set": {
                    "withdraw.allowDebit": true
                  }
                }
              }
            }
          ]
        }
      ]
    }
  ]
}
```

### Transformation how-tos:

* [JSON body](#json-body)
  * [Set body attribute to static value](#json-body-static)
  * [Set body attribute to request attribute](#json-body-ref)
  * [Drop body](#json-body-drop)
* [Headers](#headers)
* [Path parameters](#path-params)

Plugin rule configuration has following form:

```json
{
  "name": "transform-request",
  "conf": {
    "{subject}": {
        "{operation}": {
          "{attribute-identifier}": "{attribute-value-or-reference}"
        }
      }
    }
  }
}
```

e.g.

```json
{
  "name": "transform-request",
  "conf": {
    "body": {
      "set": {
        "withdraw.allowDebit": true,
        "withdraw.amount": "$headers.x-amount"
      }
    }
  }
}
```

Supported subjects with operations:
* `body` - set, drop
* `headers` - set
* `pathParams` - set

Supported reference types:
* `body`
* `headers`
* `pathParams`
* `authn`

> [Transformation details](#transformation-details).

<a id="json-body"></a>
#### JSON body

<a id="json-body-static"></a>
##### Set body attribute to static value

```json
{
  "name": "transform-request",
  "conf": {
    "body": {
      "set": {
        "withdraw.allowDebit": true
      }
    }
  }
}
```

Let's suppose that incoming request body is:

```json
{
  "accountNo": "xyz",
  "withdraw": {
    "amount": 1000
  }
}
```

then the body sent to target service is transformed to:

```json
{
  "accountNo": "xyz",
  "withdraw": {
    "amount": 1000,
    "allowDebit": true
  }
}
```

<a id="json-body-ref"></a>
##### Set body attribute to request attribute

Set JSON body attribute from header:

```json
{
  "name": "transform-request",
  "conf": {
    "body": {
      "set": {
        "accountNo": "$headers.X-Account-No"
      }
    }
  }
}
```

Let's suppose that incoming request body is:

```json
{
  "withdraw": {
    "amount": 1000
  }
}
```

given header `X-Account-No` set to `xyz` then the body sent to target service is transformed to:

```json
{
  "accountNo": "xyz",
  "withdraw": {
    "amount": 1000
  }
}
```

If we wanted to set all header values in the target request body, then the configuration would be following (note `*` at the end of the reference):

```json
{
  "name": "transform-request",
  "conf": {
    "body": {
      "set": {
        "accountNo": "$headers.X-Account-No.*"
      }
    }
  }
}
```

then target request body would look like this, provided `X-Account-No` header has two values set:

```json
{
  "accountNo": ["xyz", "abc"],
  "withdraw": {
    "amount": 1000
  }
}
```

<a id="json-body-drop"></a>
##### Drop body

Given following configuration the target request will have empty body:

```json
{
  "name": "transform-request",
  "conf": {
    "body": {
      "drop": true
    }
  }
}
```

<a id="headers"></a>
#### Headers

Set header from authentication context:

```json
{
  "name": "transform-request",
  "conf": {
    "headers": {
      "set": {
        "X-USER-ID": "$authn.userUuid"
      }
    }
  }
}
```

<a id="path-params"></a>
#### Path parameters

If `rewritePath` attribute of routing rule contains path parameter then it can be transformed.

Let's suppose we want to use header value as a path parameter.
Following configuration takes `X-USER-ID` header and uses it as `userId` path parameter.

```json
{
  "method": "GET",
  "pathPattern": "/user",
  "rewritePath": "/user/{userId}",
  "requestPlugins": [
    {
      "name": "transform-request",
      "conf": {
        "pathParams": {
          "set": {
            "userId": "$headers.X-USER-ID"
          }
        }
      }
    }
  ]
}
```

<a id="transformation-details"></a>
### Transformation details

Edge cases can get tricky, e.g. what should be a path-param value if we are setting it to header that contains multiple values?

##### Operations

Following table describes what values an operation expects. If transformation references request attribute then the value of that attribute is cast to expected value type. If it cannot be cast or found then the value is set to `null` or removed.

| Subject - Operation | Attribute identifier   | Value type      |
|---------------------|------------------------|-----------------|
| body - set          | path to JSON attribute | JSON value      |
| headers - set       | header name            | list of strings |
| pathParams - set    | path param name        | string          |

##### References

Following table describes how the request attribute are cast to required value types.

| Type                   | Example                    | Value as string                                                   | Value as list of strings                                                         | Value as JSON                              |
|------------------------|----------------------------|-------------------------------------------------------------------|----------------------------------------------------------------------------------|--------------------------------------------|
| body                   | $body.withdraw.amount      | string value if it's string or boolean or number, otherwise null  | string values if it's an array of strings or booleans or numbers, otherwise null | attribute value                            |
| pathParams             | $pathParams.accountNo      | param value                                                       | one-element list containing param value                                          | param value as JSON string                 |
| authn                  | $authn.sub                 | string value if it's string or boolean or number, otherwise null  | string values if it's an array of strings or booleans or numbers, otherwise null | attribute value                            |
| headers (first value)  | $headers.X-Account-No      | first header value                                                | one-element list containing first header value                                   | first header value as JSON string          |
| headers (all values)   | $headers.X-Forwarded-For.* | first header value                                                | all header values as list of strings                                             | all header values as JSON array of strings |