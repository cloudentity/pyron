## Plugin development guide

### Introduction

Pyron is a JVM-based application. This means you can use any Java-compatible language to implement Pyron plugin.

For Scala development tutorial go [here](plugin-development-scala.md) for Java go [here](plugin-development-java.md).

* [TL;DR; for Scala](#tldr-scala)
* [TL;DR; for Java](#tldr-java)
* [Development guidelines](#guidelines)

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

Use `RequestPluginVerticle`, `RequestPluginVerticle`, `RequestResponsePluginVerticle` depending what request-response flow part you want to extend.

```scala
case class VerifyApiKeyConf(apiKey: String)

class VerifyApiKeyPluginVerticle extends RequestPluginVerticle[VerifyApiKeyConf] with ConfigDecoder {
  // name of the plugin used in rule definition
  override def name: PluginName = PluginName("sample-verify-key")

  // transforms request (or response in case of `ResponsePluginVerticle`) given plugin configuration
  override def apply(requestCtx: RequestCtx, conf: VerifyApiKeyConf): Future[RequestCtx] = ???

  // validates plugin configuration when initializing the rule
  override def validate(conf: VerifyApiKeyConf): ValidateResponse = ???

  // returns `io.circe.Decoder` that decodes plugin configuration class from JSON
  override def confDecoder: Decoder[VerifyApiKeyConf] = io.circe.generic.semiauto.deriveDecoder
}
```

* Prepare module configuration (put it in your project at `src/main/resources/modules/plugin/sample/scala/verify-apikey.json`)

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

* Build your plugin and put the resulting JAR on Pyron classpath

Depending on Pyron deployment mode put the JAR in `run/standalone/plugin-jars` or `run/docker/plugin-jars`.

* Run Pyron with plugin enabled

Add plugin's configuration module to MODULES and set required environment variables in `run/standalone/envs` or `run/docker/envs`:

```
MODULES=["plugin/sample/scala/verify-apikey"]

PLUGIN_VERIFY_APIKEY__INVALID_STATUS_CODE=401
PLUGIN_VERIFY_APIKEY__HEADER=apikey
```

* Run Pyron with plugin enabled

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

Use `JavaRequestPluginVerticle`, `JavaRequestPluginVerticle`, `JavaRequestResponsePluginVerticle` depending what request-response flow part you want to extend.

```java
class VerifyApiKeyPluginVerticle extends JavaRequestPluginVerticle {
  // name of the plugin used in rule definition
  public String name(): {
    return "sample-verify-key";
  }

  // transforms request (or response in case of `JavaResponsePluginVerticle`) given plugin configuration
  public Future<RequestCtx> apply(RequestCtx requestCtx, JsonObject conf) {
    return null;
  }

  // validates plugin configuration when initializing the rule
  public ValidateResponse validate(JsonObject conf) {
    return null;
  }
}
```

* Prepare module configuration (put it in your project at `src/main/resources/modules/plugin/sample/java/verify-apikey.json`)

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

* Build your plugin and put the resulting JAR on Pyron classpath

Depending on Pyron deployment mode put the JAR in `run/standalone/plugin-jars` or `run/docker/plugin-jars`.

* Run Pyron with plugin enabled

Add plugin's configuration module to MODULES and set required environment variables in `run/standalone/envs` or `run/docker/envs`:

```
MODULES=["plugin/sample/java/verify-apikey"]

PLUGIN_VERIFY_APIKEY__INVALID_STATUS_CODE=401
PLUGIN_VERIFY_APIKEY__HEADER=apikey
```

* Run Pyron with plugin enabled

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

### Development guidelines

> Pyron is built on top of Vertx and [cloudentity/vertx-tools](https://github.com/Cloudentity/vertx-tools).
> A Pyron plugin is a [Vertx verticle](https://vertx.io/docs/vertx-core/java/#_verticles)
> with some extra features provided by vertx-tools' ServiceVerticle. When implementing a plugin we need to remember
> not to perform blocking-IO code unless it's deployed on separate thread pool (see for [configuration details](https://github.com/Cloudentity/vertx-tools#di-deployment-opts).

> Instead of using environment variables you can put the `verify-apikey.json` content to `system.json` in `run/standalone` or `run/docker` directory.
> However, from DevOps perspective it is more favorable to use environment variables because of ease of deployment to different environments (standalone, Kubernetes, OpenShift, etc.)
