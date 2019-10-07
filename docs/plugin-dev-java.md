## Plugin development guide for Java

The samples used in this tutorial can be found in `sample-java-plugins` module.
We use Java as programming language and Maven as build tool.

In order to build and use a plugin we need to:

* [Create a project](#project)
* [Implement a plugin class](#implement)
* [Prepare configuration module](#module)
* [Build JAR](#build)
* [Run Pyron with plugin enabled](#run)

> NOTE<br/>
> * Pyron is built on top of [Vertx](https://vertx.io) and [Cloudentity vertx-tools](https://github.com/Cloudentity/vertx-tools)
> * A Pyron plugin is a [Vertx verticle](https://vertx.io/docs/vertx-core/java/#_verticles) and [ComponentVerticle](https://github.com/Cloudentity/vertx-tools#config-verticle)
> * The lifecycle of a Pyron plugin is managed by a [verticle registry](https://github.com/Cloudentity/vertx-tools#di)

<a id="project"></a>
### Create a project

All dependencies needed to implement a plugin are contained in `pyron-plugin-java` module.

Add `com.cloudentity.pyron:pyron-plugin-java:{PYRON_VERSION}` dependency to your build.

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
    <pyron.version>PYRON_VERSION</pyron.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>com.cloudentity.pyron</groupId>
      <artifactId>pyron-plugin-java</artifactId>
      <version>${pyron.version}</version>
      <scope>provided</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.8.1</version>
        <configuration>
          <source>${java.version}</source>
          <target>${java.version}</target>
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

<a id="implement"></a>
### Implement a plugin class

There are 3 base classes you can use to implement a plugin. The one you choose depends on what request/response flow part you want to extend.

| Base class                         | Flow part          |
|:-----------------------------------|:-------------------|
| JavaRequestPluginVerticle          | request            |
| JavaResponsePluginVerticle         | response           |
| JavaRequestResponsePluginVerticle  | request + response |

As an example we will implement API key verification plugin.

The plugin verifies that the API client sends an API key in the request header matching the one configured for that API.
If they do match then the request is proxied to the target service. Otherwise, the request is aborted and error response is sent back to the client.
It is possible to configure the key value per API (at rule level per plugin application) and the key header name for all APIs an (at verticle level).

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

and verticle configuration:
```json
{
  "verticleConfig": {
    "invalidKeyStatusCode": 401,
    "defaultApiKeyHeader": "apikey"
  }
}
```

The name of the plugin is `sample-verify-apikey` and the configuration object has one attribute `apiKey` containing the API key value.

To implement the plugin logic we need to extend request part of the flow and use `RequestPluginVerticle` to create our `VerifyApiKeyPluginVerticle`.

```java
  public class VerifyApiKeyPluginVerticle extends JavaRequestPluginVerticle {
    // name of the plugin used in rule definition
    public String name() {
    }

    // transforms request (or response in case of `ResponsePluginVerticle`) given plugin configuration
    public Future<RequestCtx> applyJava(RequestCtx requestCtx, JsonObject conf) {
    }

    // validates plugin configuration when initializing the rule
    public ValidateResponse validate(JsonObject conf) {
    }
  }
```

`validate` method is called whenever rules configuration changes and plugin is applied in a rule. It checks whether the plugin configuration is valid.

In our case, `validate` method makes sure that API key in plugin configuration is not empty:

```java
  public ValidateResponse validate(JsonObject conf) {
    String apiKey = conf.getString("apiKey");
    if (apiKey == null || apiKey.isEmpty()) {
      return ValidateResponse.failure("'apiKey' must be not empty");
    } else {
      return ValidateResponse.ok();
    }
  }
```

`name` method returns plugin's name that can be referred in rule configuration:

```java
  public String name() {
    return "sample-verify-apikey";
  }
```

Now let's read verticle configuration with API key header name and response definition in case of invalid API key:

```java
  private ApiResponse unauthorizedResponse;
  private String defaultApiKeyHeader;

  @Override
  public void initService() {
    defaultApiKeyHeader = Optional.of(getConfig().getString("defaultApiKeyHeader")).get();
    unauthorizedResponse = ApiResponse.apply(
      getConfig().getInteger("invalidKeyStatusCode"),
      Buffer.buffer(),
      Headers.empty(),
      Option.apply(null)
    );
  }
```

`initService` method is executed at plugin startup. `getConfig()` method returns `io.vertx.core.JsonObject` with verticle's configuration.

And finally the plugin logic:

```java
  public Future<RequestCtx> applyJava(RequestCtx requestCtx, JsonObject conf) {
    Option<String> apiKeyValueOpt = requestCtx.request().headers().get(defaultApiKeyHeader);

    if (apiKeyValueOpt.isDefined()) {
      // continue request flow
      return Future.succeededFuture(requestCtx);
    } else {
      // abort request and return response to the client
      return Future.succeededFuture(requestCtx.abort(unauthorizedResponse));
    }
  }
```

`applyJava` method accepts `RequestCtx` and plugin configuration. Remember that `RequestCtx` is immutable, so it requires [special handling](#docs/plugin-dev-ctx-java.md).

Finally, the full implementation:

```java
public class VerifyApiKeyPluginVerticle extends JavaRequestPluginVerticle {
  @Override
  public String name() {
    return "sample-verify-apikey";
  }

  private ApiResponse unauthorizedResponse;
  private String defaultApiKeyHeader;

  @Override
  public void initService() {
    defaultApiKeyHeader = Optional.of(getConfig().getString("defaultApiKeyHeader")).get();
    unauthorizedResponse = ApiResponse.apply(
      getConfig().getInteger("invalidKeyStatusCode"),
      Buffer.buffer(),
      Headers.empty(),
      Option.apply(null)
    );
  }

  @Override
  public Future<RequestCtx> applyJava(RequestCtx requestCtx, JsonObject conf) {
      Option<String> apiKeyValueOpt = requestCtx.request().headers().get(defaultApiKeyHeader);

      if (apiKeyValueOpt.isDefined()) {
        // continue request flow
        return Future.succeededFuture(requestCtx);
      } else {
        // abort request and return response to the client
        return Future.succeededFuture(requestCtx.abort(unauthorizedResponse));
      }
  }

  @Override
  public ValidateResponse validate(JsonObject conf) {
    String apiKey = conf.getString("apiKey");
    if (apiKey == null || apiKey.isEmpty()) {
      return ValidateResponse.failure("'apiKey' must be not empty");
    } else {
      return ValidateResponse.ok();
    }
  }
}
```

<a id="module"></a>
### Prepare configuration module

In order to deploy `VerifyApiKeyPluginVerticle` we need to put it's definition in `request-plugins` [registry](https://github.com/Cloudentity/vertx-tools#di).

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

| Attribute         | Description                                                            |
|:------------------|:-----------------------------------------------------------------------|
| main              | Class name to deploy                                                   |
| verticleConfig    | Configuration injected into plugin verticle; returned by `getConfig()` |

> NOTE<br/>
> Pyron supports cross-configuration, environment variables and system properties [references](https://github.com/Cloudentity/vertx-tools#config-references).
> `verticleConfig.invalidKeyStatusCode` is set to `PLUGIN_VERIFY_APIKEY__INVALID_STATUS_CODE` environment variable reference with default `401` integer value.

We need to put the above JSON file on plugin JAR classpath at `modules/plugin/...` path, e.g. `modules/plugin/sample/java/verify-apikey.json`.
Later on, we will configure Pyron to read this JSON and deploy the plugin.

<a id="build"></a>
### Build JAR

Run `mvn install` to build JAR containing plugin verticle class `VerifyApiKeyPluginVerticle` and config module `modules/plugin/sample/java/verify-apikey.json`.
The JAR needs to be put on Pyron's classpath, so the verticle registry can deploy it. Depending on Pyron deployment mode put it in `run/standalone/plugin-jars` or `run/docker/plugin-jars`.

<a id="run"></a>
### Run Pyron with plugin enabled

Add plugin's configuration module to MODULES and set required environment variables in `run/standalone/envs` or `run/docker/envs`:

```
MODULES=["plugin/sample/java/verify-apikey"]

PLUGIN_VERIFY_APIKEY__INVALID_STATUS_CODE=401
PLUGIN_VERIFY_APIKEY__HEADER=apikey
```

Start Pyron. Now we can use the plugin in a rule:

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

> NOTE<br/>
> It is possible to deploy/undeploy plugins at runtime, but it requires to override MODULES environment variable in `system.json` configuration file.
> [Read about the details](https://github.com/Cloudentity/vertx-tools#override-envsys).