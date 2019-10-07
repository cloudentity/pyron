## Plugin development guide

### Introduction

Pyron is a JVM-based application. This means you can use any Java-compatible language to implement Pyron plugin.

For Scala development tutorial go [here](plugin-dev-scala.md) for Java go [here](plugin-dev-java.md).

* [TL;DR; for Scala](#tldr-scala)
* [TL;DR; for Java](#tldr-java)

<a id="tldr-scala"></a>
#### TL;DR; for Scala

* Add `pyron-plugin` dependency to your pom.xml

```xml
<dependency>
  <groupId>com.cloudentity.pyron</groupId>
  <artifactId>pyron-plugin</artifactId>
  <version>${pyron.version}</version>
  <scope>provided</scope>
</dependency>
```

* Implement plugin verticle

Use `RequestPluginVerticle`, `ResponsePluginVerticle`, `RequestResponsePluginVerticle` depending what request-response flow part you want to extend.

```scala
case class VerifyApiKeyConf(apiKey: String)

class VerifyApiKeyPluginVerticle extends RequestPluginVerticle[VerifyApiKeyConf] with ConfigDecoder {
  // name of the plugin used in rule definition
  override def name: PluginName = PluginName("sample-verify-key")

  // transforms request (or response in case of `ResponsePluginVerticle`) given plugin configuration, NOTE: `RequestCtx` is immutable
  override def apply(requestCtx: RequestCtx, conf: VerifyApiKeyConf): Future[RequestCtx] = ???

  // validates plugin configuration when initializing the rule
  override def validate(conf: VerifyApiKeyConf): ValidateResponse = ???

  // returns `io.circe.Decoder` that decodes plugin configuration class from JSON
  override def confDecoder: Decoder[VerifyApiKeyConf] = io.circe.generic.semiauto.deriveDecoder
}
```

* Prepare plugin module configuration

```json
{
  "registry:request-plugins": {
    "sample-verify-apikey": {
      "main": "com.cloudentity.pyron.sample.scala.VerifyApiKeyPluginVerticle",
      "verticleConfig": {
        "invalidKeyStatusCode": "$env:PLUGIN_VERIFY_APIKEY__INVALID_STATUS_CODE:int:401",
        "defaultApiKeyHeader": "$env:PLUGIN_VERIFY_APIKEY__HEADER:string:apikey"
      }
    }
  }
}
```

Put it in your plugin JAR classpath at `modules/plugin/{path-to-you-plugin-verticle-config}.json`, e.g. in your sources root at `src/main/resources/modules/plugin/sample/scala/verify-apikey.json`.

* Build your plugin and put the resulting JAR on Pyron classpath

Depending on Pyron deployment mode put the JAR in `run/standalone/plugin-jars` or `run/docker/plugin-jars`.

* Run Pyron with plugin enabled

Add plugin's configuration module to MODULES and set required environment variables in `run/standalone/envs` or `run/docker/envs`:

```
MODULES=["plugin/sample/scala/verify-apikey"]

PLUGIN_VERIFY_APIKEY__INVALID_STATUS_CODE=401
PLUGIN_VERIFY_APIKEY__HEADER=apikey
```

* Use plugin in a rule

```json
{
  "rules": [
    {
      "default": {
        "targetHost": "example.com",
        "targetPort": 8000
      },
      "endpoints": [
        {
          "method": "POST",
          "pathPattern": "/user",
          "requestPlugins": [
            {
              "name": "sample-verify-apikey",
              "conf": {
                "apiKey": "secret-api-key"
              }
            }
          ]
        }
      ]
    }
  ]
}
```

<a id="tldr-java"></a>
#### TL;DR; for Java

* Add `pyron-plugin-java` dependency to your pom.xml

```xml
<dependency>
  <groupId>com.cloudentity.pyron</groupId>
  <artifactId>pyron-plugin-java</artifactId>
  <version>${pyron.version}</version>
  <scope>provided</scope>
</dependency>
```

* Implement plugin verticle

Use `JavaRequestPluginVerticle`, `JavaResponsePluginVerticle`, `JavaRequestResponsePluginVerticle` depending what request-response flow part you want to extend.

```java
public class VerifyApiKeyPluginVerticle extends JavaRequestPluginVerticle {
  // name of the plugin used in rule definition
  public String name() {
    return "sample-verify-key";
  }

  // transforms request (or response in case of `JavaResponsePluginVerticle`) given plugin configuration, NOTE: `RequestCtx` is immutable
  public Future<RequestCtx> apply(RequestCtx requestCtx, JsonObject conf) {
    return null;
  }

  // validates plugin configuration when initializing the rule
  public ValidateResponse validate(JsonObject conf) {
    return null;
  }
}
```

* Prepare module configuration

```json
{
  "registry:request-plugins": {
    "sample-verify-apikey": {
      "main": "com.cloudentity.pyron.sample.java.VerifyApiKeyPluginVerticle",
      "verticleConfig": {
        "invalidKeyStatusCode": "$env:PLUGIN_VERIFY_APIKEY__INVALID_STATUS_CODE:int:401",
        "defaultApiKeyHeader": "$env:PLUGIN_VERIFY_APIKEY__HEADER:string:apikey"
      }
    }
  }
}
```

Put it in your plugin JAR classpath at `modules/plugin/{path-to-you-plugin-verticle-config}.json`, e.g. in your sources root at `src/main/resources/modules/plugin/sample/java/vertify-apikey.json`.

* Build your plugin and put the resulting JAR on Pyron classpath

Depending on Pyron deployment mode put the JAR in `run/standalone/plugin-jars` or `run/docker/plugin-jars`.

* Run Pyron with plugin enabled

Add plugin's configuration module to MODULES and set required environment variables in `run/standalone/envs` or `run/docker/envs`:

```
MODULES=["plugin/sample/java/verify-apikey"]

PLUGIN_VERIFY_APIKEY__INVALID_STATUS_CODE=401
PLUGIN_VERIFY_APIKEY__HEADER=apikey
```

* Use plugin in a rule

```json
{
  "rules": [
    {
      "default": {
        "targetHost": "example.com",
        "targetPort": 8000
      },
      "endpoints": [
        {
          "method": "POST",
          "pathPattern": "/user",
          "requestPlugins": [
            {
              "name": "sample-verify-apikey",
              "conf": {
                "apiKey": "secret-api-key"
              }
            }
          ]
        }
      ]
    }
  ]
}
```
