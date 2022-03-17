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
  * [Set body attribute to request attribute with default value](#json-body-with-default-ref)
  * [Remove a body attribute](#json-body-remove)
  * [Drop body](#json-body-drop)
* [Headers](#headers)
* [Path parameters](#path-params)
* [Conf references](#conf-ref)

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
* `body` - set, setWithDefault, remove, drop
* `headers` - set
* `pathParams` - set
* `queryParams` - set

Supported reference types with sub-items:
* `body` (alternatively `req.body`)
* `headers` (alternatively `req.headers`)
* `pathParams`
* `queryParams`
* `cookies`
* `authn`
* `conf`

Supported basic references types without sub-items:
* `scheme` - original request scheme
* `host` - original request host (name and port)
* `hostName` - original request host name only 
* `hostPort` - original request host port only
* `localHost` - original request local host
* `remoteHost` - original request remote host

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

Elements of JSON arrays may also be referenced using the configuration syntax `.[array_index]`. For example, suppose the incoming request body is:

```json
{
  "accounts": [
    {
      "name": "Savings",
      "balance": "20000"
    },
    {
      "name": "Checking",
      "balance": "1000"
    }
  ]
}
```

To set a request body attribute based on the head element of the `accounts` array, the following configuration should be used:

```json
{
  "name": "transform-request",
  "conf": {
    "body": {
      "set": {
        "primaryAccount": "$body.accounts.[0]"
      }
    }
  }
}
```

Then the target request body would be:

```json
{
  "accounts": [
    {
      "name": "Savings",
      "balance": "20000"
    },
    {
      "name": "Checking",
      "balance": "1000"
    }
  ],
  "primaryAccount": {
    "name": "Savings",
    "balance": "20000"
  }
}
```

The array element can be of any type. If the array element is itself a JSON array or object, its own nested attributes may be further referenced after the initial array element reference.

In the above example, if the following configuration is used instead:

```json
{
  "name": "transform-request",
  "conf": {
    "body": {
      "set": {
        "primaryAccountName": "$body.accounts.[0].name"
      }
    }
  }
}
```

then the target request body would be:

```json
{
  "accounts": [
    {
      "name": "Savings",
      "balance": "20000"
    },
    {
      "name": "Checking",
      "balance": "1000"
    }
  ],
  "primaryAccountName": "Savings"
}
```

Suppose a value referenced by `set` is not found in the request body. Using the above example, consider the following configuration (note that, with only 2 total array elements, the element of index 2 does not exist):

```json
{
  "name": "transform-request",
  "conf": {
    "body": {
      "set": {
        "tertiaryAccountName": "$body.accounts.[2].name"
      }
    }
  }
}
```

By default, the JSON value `null` is set:

```json
{
  "accounts": [
    {
      "name": "Savings",
      "balance": "20000"
    },
    {
      "name": "Checking",
      "balance": "1000"
    }
  ],
  "tertiaryAccountName": null
}
```

If the configuration flag `nullIfAbsent` is set to `false` in the body config, then any values mapped by `set` which are not found will be omitted from the result, rather than set to `null`:

```json
{
  "name": "transform-request",
  "conf": {
    "body": {
      "nullIfAbsent": false,
      "set": {
        "tertiaryAccountName": "$body.accounts.[2].name"
      }
    }
  }
}
```

Note that, now that `nullIfAbsent` is set to `false` in the configuration, the key `tertiaryAccountName` is omitted from the target request body:

```json
{
  "accounts": [
    {
      "name": "Savings",
      "balance": "20000"
    },
    {
      "name": "Checking",
      "balance": "1000"
    }
  ]
}
```

<a id="json-body-with-default-ref"></a>
##### Set body attribute to request attribute with default value

Similar to the `set` configuration block, the `setWithDefault` configuration allows adding or modifying values in the request body. However, it adds options for setting a default value, in case the referenced attribute is not found or has a null value.

The `setWithDefault` configuration block has the following format:

```json
{
  "{attribute-identifier}": {
    "sourcePath": "{attribute-value-or-reference}",
    "ifNull": {value-or-remove},
    "ifAbsent": {value-or-remove}
  }
}
```

where `{value-or-remove}` is a JSON object in either of the following formats:

```json
{"value": some-json-value}
```

OR

```json
{"remove": true}
```

`ifNull` and `ifAbsent` are both optional:
* `ifNull`: What to do if the referenced attribute is explicitly set to `null`. Default config value: `{"value": null}` - retains null value if found
* `ifAbsent`: What to do if the referenced attribute key is not found at all. Default config value: `{"remove": true}` - retains omitted value if not found

Suppose that a downstream service requires an account name in the request body for each transaction, and a default value of "defaultAccount" is acceptable for clients. Then the following config is applicable:

```json
{
  "name": "transform-request",
  "conf": {
    "body": {
      "setWithDefault": {
        "accountName": {
          "sourcePath": "$body.accountName",
          "ifNull": {"value": "defaultAccount"},
          "ifAbsent": {"value": "defaultAccount"}
        }
      }
    }
  }
}
```

If the value is supplied, it will be used:

```json
{
  "accountName": "Checking",
  "withdraw": {
    "amount": 1000
  }
}
```

However, if the value is not supplied, or is null, the default will be used. In other words, for either of the following request bodies:

```json
{
  "accountName": null,
  "withdraw": {
    "amount": 1000
  }
}
```

```json
{
  "withdraw": {
    "amount": 1000
  }
}
```

the request will be translated into:

```json
{
  "accountName": "defaultAccount",
  "withdraw": {
    "amount": 1000
  }
}
```

Note that the value for the "value" field can be any valid JSON.

In some cases, it may be desirable to remove a JSON key-value pair completely if it has a null value. In such a case, for example, the following config may be used:

```json
{
  "name": "transform-request",
  "conf": {
    "body": {
      "setWithDefault": {
        "accountName": {
          "sourcePath": "$body.accountName",
          "ifNull": {"remove": true}
        }
      }
    }
  }
}
```

Then for the following request body:

```json
{
  "accountName": null,
  "withdraw": {
    "amount": 1000
  }
}
```

the transformed body will be:

```json
{
  "withdraw": {
    "amount": 1000
  }
}
```

Note that, because the null-value case is handled explicitly, the `nullIfAbsent` configuration is ignored for the `setWithDefault` block.

<a id="json-body-remove"></a>
##### Remove a body attribute

Individual elements can be removed from the request body. Use the `.` and `.[array_index]` configuration syntax, as in the `set` directive, to reference elements of a JSON path to be removed (no leading `$body.` is needed, however).

For example, suppose the request body is:

```json
{
  "accountNo": "xyz",
  "routingNo": "123",
  "withdraw": {
    "amount": 1000,
    "allowDebit": true
  },
  "accounts": [
    {
      "name": "Savings",
      "balance": "20000"
    },
    {
      "name": "Checking",
      "balance": "1000"
    }
  ]
}
```

If the following configuration is used:
```json
{
  "name": "transform-request",
  "conf": {
    "body": {
      "remove": [
        "routingNo",
        "withdraw.allowDebit",
        "accounts.[1]"
      ]
    }
  }
}
```

then the target request body will be:

```json
{
  "accountNo": "xyz",
  "withdraw": {
    "amount": 1000
  },
  "accounts": [
    {
      "name": "Savings",
      "balance": "20000"
    }
  ]
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

##### Setting headers using pattern matching

You can set header to a value retrieved by applying a pattern against strings values.

Usually we would set `"path"` field to reference some array of strings, which are our candidates for matching the `"pattern"`.
All strings which match the pattern end-to-end will be used to calculate values for the header.

Each time we find a value matching the pattern, parts of the match will be captured into parameters defined on the pattern.
Once captured, parameter values replace corresponding placeholders of the  `"output"`, to calculate value for the header.

The header is set only if at least one matching value is found.
When one value matches the pattern, single header value will be calculated and set.
When multiple values match, a list of values will be calculated and header will be set to contain the list.

To define parameters within the pattern, we surround param identifier with curly braces, all other characters must match exactly and literally.
Placeholders for injecting captured parameter values on the output field are defined in the same way.
Parameter names should consist of upper and lower case letters and numbers.

This functionality can be used to define dynamic scopes.

Let's consider a simple configuration:

```json
{
  "name": "transform-request",
  "conf": {
    "headers": {
      "set": {
        "X-Transaction": {
          "path": "$authn.scp",
          "pattern": "transaction-{id}",
          "output": "{id}"
        }
      }
    }
  }
}
```
In above example we want to retrieve value starting with `transaction-` from contents of `scp` array defined in authentication context.
We use value of `id` parameter, captured by `{id}` in our pattern and then referenced as `{id}` in the output.
Here `id` will capture whatever follows the `transaction-` prefix.
The value of `X-Transaction` header will be set to the calculated value of the output, with parameters applied.

Within `pattern` we can specify required length or length range for a named parameter, by using `:` symbol.
For example `{id:4}` would only match a string of 4 chars, while `{id:4:8}` would match a string of 4 to 8 chars length.
If length criteria is specified it must be fulfilled for transformation to be applied.

If authentication context contains:
```json
{
  "scp": [
    "payment-XYZ", "transaction-123", "unrelated-value"
  ],
  "other_fields": "other_values ..."
}
```
then the pattern will search through values inside `scp`, it will successfully match on the second value, apply param captured by `{id}` on the `output` and set `X-Transaction` to `"123"`

If authentication context contains two values matching the pattern:
```json
{
  "scp": [
    "payment-XYZ", "transaction-123", "transaction-456", "unrelated-value"
  ],
  "other_fields": "other_values ..."
}
```
set `X-Transaction` to `"123,456"`

We can use multiple parameters and reorder them freely to build the output value for the header.
Given config:
```json
{
  "name": "transform-request",
  "conf": {
    "headers": {
      "set": {
        "X-Transaction": {
          "path": "$authn.scp",
          "pattern": "transaction-{transactionId}-swift-{swiftId}",
          "output": "TX-{swiftId}_{transactionId}"
        }
      }
    }
  }
}
```
with input from authentication context similar to:
```json
{
  "scp": [
    "payment-XYZ", "transaction-123-swift-AXZ", "unrelated-value"
  ],
  "other_fields": "other_values ..."
}
```
`X-Transaction` header will be set to `"TX-AXZ_123"`

If the `scp` field is not an array, then string representation of the value is used, as long as it matches the pattern.
Non-array value is therefore treated the same way as a single-item list.
With the same config, using the input below, where `"scp"` is a string, we still get a match, and `X-Transaction` header is set to `"TX-AXZ:123"`
```json
{
  "scp": "transaction-123-swift-AXZ",
  "other_fields": "other_values ..."
}
```
Values from other supported references such as `$body` can be used just fine in `"path"` instead of `$authn`.
Also, the `scp` field used here is just an example and different field can be used in `"path"`.
Curly braces themselves can still be used to match literal curly braces within value, by doubling them, so {{ and }} will match { and }.
If non-double curly brace is encountered in `"pattern"`, other than around parameter definition, it will be stripped from the pattern.

If we define exact match without params, we can map header to some fixed value. Given:
```json
{
  "name": "transform-request",
  "conf": {
    "headers": {
      "set": {
        "X-Access": {
          "path": "$authn.group",
          "pattern": "admin",
          "output": "privileged"
        }
      }
    }
  }
}
```
with input from authentication context similar to:
```json
{
  "group": "admin",
  "other_fields": "other_values ..."
}
```
`X-Access` header will be set to `"privileged"`.

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

<a id="conf-ref"></a>
### Conf references

You can use `$conf` reference type to refer to values from Pyron configuration.
Set `PLUGIN_TRANSFORM_REQUEST_CONF_REF` environment variable to define reference to configuration that will be available at `$conf`.

E.g.:
* PLUGIN_TRANSFORM_REQUEST_CONF_REF=$ref:secrets.consul
* Pyron configuration:
```json
{
  "secrets": {
    "consul": {
      "token": "xyz"
    }
  }
}
```

Given following plugin configuration:

```json
{
  "method": "GET",
  "pathPattern": "/user",
  "requestPlugins": [
    {
      "name": "transform-request",
      "conf": {
        "headers": {
          "set": {
            "X-Consul-Token": "$conf.token"
          }
        }
      }
    }
  ]
}
```

when Pyron receives original request at `/user` path it will set `X-Consul-Token` header to `xyz` in the target request.


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
