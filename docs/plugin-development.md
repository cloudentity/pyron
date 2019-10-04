## Plugin development guide

### Introduction

Pyron is a JVM-based application. This means you can use any Java-compatible language to implement Pyron plugin.
The samples used in this tutorial can be found in `sample-scala-plugins` module.
We use Scala as programming language and Maven as build tool.

In order to build and use a plugin we need to:

* [Create a project](#project)
* [Implement a plugin class](#implement)
* [Prepare configuration module](#module)
* [Build JAR](#build)
* [Run Pyron with plugin enabled](#run)

#### TL;DR;

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

> Instead of using environment variables you can put the `verify-apikey.json` content to `system.json` in `run/standalone` or `run/docker` directory.
> However, from DevOps perspective it is more favorable to use environment variables because of ease of deployment to different environments (standalone, Kubernetes, OpenShift, etc.)

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

### Create a project

All dependencies needed to implement a plugin are contained in `pyron-plugin` module.

Add com.cloudentity.pyron:pyron-plugin:{pyron.version} dependency to your build.

Sample `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <groupId>com.cloudentity.pyron.plugin.custom</groupId>
  <artifactId>pyron-plugin-verify-apikey</artifactId>
  <version>1.0</version>
  <modelVersion>4.0.0</modelVersion>
  <packaging>jar</packaging>

  <properties>
    <scala.version>2.12.9</scala.version>
    <scala-maven-plugin.version>4.2.0</scala-maven-plugin.version>
    <pyron.version>1.0.0-SNAPSHOT</pyron.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.scala-lang</groupId>
      <artifactId>scala-library</artifactId>
      <version>${scala.version}</version>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>com.cloudentity.pyron</groupId>
      <artifactId>pyron-plugin</artifactId>
      <version>${pyron.version}</version>
      <scope>provided</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>net.alchim31.maven</groupId>
        <artifactId>scala-maven-plugin</artifactId>
        <version>${scala-maven-plugin.version}</version>
        <executions>
          <execution>
            <goals>
              <goal>compile</goal>
              <goal>testCompile</goal>
            </goals>
          </execution>
        </executions>
        <configuration>
          <scalaVersion>${scala.version}</scalaVersion>
          <args>
            <arg>-unchecked</arg>
            <arg>-deprecation</arg>
            <arg>-Xfatal-warnings</arg>
            <arg>-language:postfixOps</arg>
          </args>
        </configuration>
      </plugin>

      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>2.3</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals>
              <goal>shade</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>

</project>
```

### Implement a plugin class

There are 3 base classes you can use to implement a plugin. The one you choose depends on what request/response flow part you want to extend.

| Base class                     | Flow part          |
|:-------------------------------|:-------------------|
| RequestPluginVerticle          | request            |
| ResponsePluginVerticle         | response           |
| RequestResponsePluginVerticle  | request + response |

```example
As an example we will implement API key verification plugin.

The plugin verifies that the API client sends an API key in the request header that matches the one configured for that API.
If they do match then the request is proxied to the target service. Otherwise, the request is aborted and error response is sent back to the client.
It is possible to configure the key header name for all APIs and the key value per API (at rule level).

The plugin rule configuration has following form:

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

The name of the plugin is `sample-verify-apikey` and configuration attribute with API key value is `apiKey`.

The example requirements mean that we are extending request flow part and we need to use `RequestPluginVerticle` to implement our `VerifyApiKeyPluginVerticle`.

```scala
class VerifyApiKeyPluginVerticle extends RequestPluginVerticle[VerifyApiKeyConf] with ConfigDecoder {
  // name of the plugin used in rule definition
  override def name: PluginName

  // transforms request (or response in case of `ResponsePluginVerticle`) given plugin configuration
  override def apply(requestCtx: RequestCtx, conf: VerifyApiKeyConf): Future[RequestCtx]

  // validates plugin configuration when initializing the rule
  override def validate(conf: VerifyApiKeyConf): ValidateResponse

  // returns `io.circe.Decoder` that decodes plugin configuration class from JSON
  override def confDecoder: Decoder[VerifyApiKeyConf]
}
```

`RequestPluginVerticle` is parametrized with `VerifyApiKeyConf`, i.e. a class representing plugin configuration at rule level.

```example
In our case it is `case class VerifyApiKeyConf(apiKey: String)`.
```

We can use automatic derivation of Decoder using `io.circe.generic.semiauto.deriveDecoder` method or create custom implementation.

```scala
override def confDecoder: Decoder[VerifyApiKeyConf] = io.circe.generic.semiauto.deriveDecoder
```

Making sure that API key in plugin configuration is not empty:

```scala
override def validate(conf: VerifyApiKeyConf): ValidateResponse =
    if (conf.apiKey.nonEmpty) ValidateResponse.ok()
    else                      ValidateResponse.failure("'apiKey' must be not empty")
```

Setting plugin's name:

```scala
override def name: PluginName = PluginName("sample-verify-apikey")
```

Now let's read `VerifyApiKeyVerticleConf`, i.e. verticle configuration with API key header and response definition in case of invalid API key:

```scala
case class VerifyApiKeyVerticleConf(invalidKeyStatusCode: Int, defaultApiKeyHeader: String)
```

```scala
var verticleConf: VerifyApiKeyVerticleConf = _
var unauthorizedResponse: ApiResponse = _

override def initService(): Unit = {
  implicit val PluginConfDecoder = deriveDecoder[VerifyApiKeyVerticleConf]
  verticleConf = decodeConfigUnsafe[VerifyApiKeyVerticleConf]

  unauthorizedResponse =
    ApiResponse(
      statusCode = verticleConf.invalidKeyStatusCode,
      body       = Buffer.buffer(),
      headers    = Headers()
    )
}
```

`initService` method is executed at plugin startup. Here we decode verticle's configuration using `decodeConfigUnsafe`.
Alternatively, we can use `getConfig()` method to get `io.vertx.core.JsonObject` with raw verticle's configuration.

And finally the plugin logic:

```scala
override def apply(requestCtx: RequestCtx, conf: VerifyApiKeyConf): Future[RequestCtx] =
  Future.successful {
    val apiKeyValueOpt = requestCtx.request.headers.get(verticleConf.defaultApiKeyHeader)

    apiKeyValueOpt match {
      case Some(value) if (value == conf.apiKey) =>
        requestCtx // continue request flow
      case _ =>
        requestCtx.abort(unauthorizedResponse) // abort request and return response to the client
    }
  }
```

`apply` method accepts `RequestCtx` and plugin configuration. `RequestCtx` is immutable, so

### Notes

Pyron is built on top of Vertx and [cloudentity/vertx-tools](https://github.com/Cloudentity/vertx-tools).
A Pyron plugin is a [Vertx verticle](https://vertx.io/docs/vertx-core/java/#_verticles)
with some extra features provided by vertx-tools' ServiceVerticle. When implementing a plugin we need to remember
not to perform blocking-IO code unless it's deployed on separate thread pool (see for [configuration details](https://github.com/Cloudentity/vertx-tools#di-deployment-opts).
