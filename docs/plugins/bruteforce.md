## Brute-force protection plugin

`bruteForce` plugin protects against brute force attacks.

Enable `bruteForce` plugin with in-memory storage by adding `plugin/bruteforce` to `MODULES` environment variable.

> NOTE<br/>
> Brute-force attempts are stored locally - the attempt counters are not shared between multiple Pyron nodes.

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
          "pathPattern": "/login",
          "requestPlugins": [
            {
              "name": "bruteForce",
              "conf": {
                "counterName": "login",
                "identifier": {
                  "location": "body",
                  "name": "uid"
                },
                "successCodes": [200, 201],
                "errorCodes": [400, 401],
                "maxAttempts": 3,
                "blockSpan": 5,
                "blockFor": 10,
                "lockedResponse": {
                  "code": "Authentication.Locked",
                  "message": "The maximum number of login attempts has been reached."
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

Configuration attributes:

| Name            | Description                                                                                                                       |
|:----------------|:----------------------------------------------------------------------------------------------------------------------------------|
| counterName     | brute-force attempts counter                                                                                                      |
| identifier      | defines the protected entity identifier, `identifier.location` can be "header" or "body"                                          |
| identifier.name | header name in case of `identifier.location` is `header` or JSON object attribute path in case of `identifier.location` is `body` |
| successCodes    | array of response status codes that clear the brute-force counter for the identifier                                              |
| errorCodes      | array of response status codes that increase the brute-force attempts counter for the identifier                                  |
| maxAttempts     | maximum number of failed calls (with response status code matching `errorCodes`) before an entity is locked out                   |
| blockSpan       | length of time (sec) a failed attempt is remembered                                                                               |
| blockFor        | length of time (sec) the entity will be locked out                                                                                |
| lockedResponse  | JSON body returned if API is locked                                                                                               |
| identifierCaseSensitive  | Indicates whether the value of identifier used to track the counter is case sensitive. Default(or if not set) value is `false` (i.e the identifier value counter is tracked in a case insensitive manner)                                                                                             |

### Example

In the above sample configuration, the max attempts is 3, so the 3rd unsuccessful attempt within the `blockSpan` period (5 seconds) will block the next attempt for `blockFor` period (10 seconds).

If before being blocked a successful attempt is made, as defined by the `successCodes` (here 200 or 201), then the failed attempts counter is reset to 0. The plugin associates the failed attempts with an entity defined in the 'identifier' block, here telling it to associate it with the `uid` in the request body.
