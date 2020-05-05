package com.cloudentity.pyron.apigroup

import com.cloudentity.pyron.rule.RulesStoreVerticle
import com.cloudentity.tools.vertx.bus.ServiceClientFactory
import com.cloudentity.tools.vertx.conf.ConfVerticleDeploy
import com.cloudentity.tools.vertx.test.VertxUnitTest
import com.cloudentity.tools.vertx.verticles.VertxDeploy
import io.vertx.core.{Future => VxFuture}
import io.vertx.ext.unit.TestContext
import org.junit.Test

class ApiGroupsStoreVerticleSpec extends VertxUnitTest {

  @Test
  def shouldLoadDeprecateRulesAsDefaultApiGroup(ctx: TestContext): Unit = {
    deployAndGetRules("src/test/resources/rules-store/config-rules.json")
      .compose { (groups: List[ApiGroup]) =>
        ctx.assertEquals(groups.size, 1)
        ctx.assertEquals(groups.head.id, ApiGroupId("default"))

        VxFuture.succeededFuture(())
      }.setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def shouldLoadSingleRuleWithoutPlugins(ctx: TestContext): Unit = {
    deployAndGetRules("src/test/resources/rules-store/config-single-wo-plugins.json")
      .compose { (groups: List[ApiGroup]) =>
        val rules = groups.flatMap(_.rules)
        ctx.assertEquals(rules.size, 1)
        ctx.assertEquals(rules.head.requestPlugins.size, 0)

        VxFuture.succeededFuture(())
      }.setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def shouldLoadSingleRuleWithPluginExtendingRules(ctx: TestContext): Unit = {
    deployAndGetRules("src/test/resources/rules-store/config-single-w-plugin-extending-rule.json", VertxDeploy.deploy(vertx, new ExtendRulesPlugin))
      .compose { (groups: List[ApiGroup]) =>
        val rules = groups.flatMap(_.rules)
        ctx.assertEquals(rules.size, 3)
        ctx.assertEquals(rules.flatMap(_.conf.endpointName), List("prepended", "original", "appended"))

        VxFuture.succeededFuture(())
      }.setHandler(ctx.asyncAssertSuccess())
  }

  def deployAndGetRules(configPath: String): VxFuture[List[ApiGroup]] =
    deployAndGetRules(configPath, VxFuture.succeededFuture(()))

  def deployAndGetRules[A](configPath: String, init: VxFuture[A]): VxFuture[List[ApiGroup]] =
    ConfVerticleDeploy.deployFileConfVerticle(vertx, configPath)
      .compose(_ => init)
      .compose { _ => VertxDeploy.deploy(vertx, new RulesStoreVerticle) }
      .compose { _ => VertxDeploy.deploy(vertx, new ApiGroupsStoreVerticle) }
      .compose { _ => VxFuture.succeededFuture(ServiceClientFactory.make(vertx.eventBus(), classOf[ApiGroupsStore])) }
      .compose { (client: ApiGroupsStore) => client.getGroups() }
}
