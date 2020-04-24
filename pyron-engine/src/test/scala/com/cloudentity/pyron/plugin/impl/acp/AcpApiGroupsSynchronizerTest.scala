package com.cloudentity.pyron.plugin.impl.acp

import com.cloudentity.pyron.PyronAcceptanceTest
import com.cloudentity.pyron.apigroup.{ApiGroupConf, ApiGroupId, ApiGroupsChangeListener}
import com.cloudentity.pyron.domain.flow.{BasePath, GroupMatchCriteria}
import com.cloudentity.pyron.rule.RulesConfReader
import com.cloudentity.tools.vertx.bus.ServiceClientFactory
import io.vertx.core.{Future, Promise}
import io.vertx.ext.unit.TestContext
import org.junit.{After, Before, Test}
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response

class AcpApiGroupsSynchronizerTest extends PyronAcceptanceTest {
  override val getMetaConfPath = "src/test/resources/modules/plugin/acp-authz/meta-config.json"

  var authorizer: ClientAndServer = null

  @Before
  override def setUp(ctx: TestContext): Unit = {
    authorizer = ClientAndServer.startClientAndServer(7777)
    authorizer
      .when(request().withPath("/apis"))
      .respond(response.withStatusCode(204))

    super.setUp(ctx)
  }

  @After
  def finish(): Unit = {
    authorizer.stop()
  }

  @Test
  def shouldSendToAuthorizerAtStartup(ctx: TestContext): Unit = {
    // given
    val expectedBody = """{"api_groups":[{"id":"a.1","apis":[{"method":"GET","path":"/a/1/user/{userid}"}]}]}"""

    // then
    verify("PUT", "/apis", expectedBody).setHandler(ctx.asyncAssertSuccess())
  }

  @Test
  def shouldSendToAuthorizerOnApiGroupUpdate(ctx: TestContext): Unit = {
    // given
    val rules = """[
                  |  {
                  |    "endpoints": [
                  |      {
                  |        "targetHost": "localhost",
                  |        "targetPort": 7760,
                  |        "method": "GET",
                  |        "pathPattern": "/payments",
                  |        "requestPlugins": [
                  |          { "name": "acp-authz" }
                  |        ]
                  |      },
                  |      {
                  |        "targetHost": "localhost",
                  |        "targetPort": 7760,
                  |        "method": "GET",
                  |        "pathPattern": "/account"
                  |      }
                  |    ]
                  |  }
                  |]
                  |""".stripMargin

    val groups =
      List(
        ApiGroupConf(
          ApiGroupId("a.1"),
          GroupMatchCriteria(Some(BasePath("/a/1")), None),
          RulesConfReader.read(rules, Map()).toOption.get
        )
      )

    val expectedBody = """{"api_groups":[{"id":"a.1","apis":[{"method":"GET","path":"/a/1/payments"}]}]}"""

    // when
    ServiceClientFactory.make(vertx.eventBus(), classOf[ApiGroupsChangeListener]).apiGroupsChanged(Nil, groups)

    // then
    verify("PUT", "/apis", expectedBody).setHandler(ctx.asyncAssertSuccess())
  }

  def verify(method: String, path: String, expectedBody: String): Future[Unit] = {
    val promise = Promise.promise[Unit]()
    def rec(p: Promise[Unit], t: Int, ex: Option[Throwable]): Unit = {
      if (t > 0 ) {
        vertx.setTimer(50, _ => {
          try {
            authorizer.verify(request().withMethod(method).withPath(path).withBody(expectedBody))
            promise.complete(())
          } catch {
            case ex: Throwable =>
              rec(p, t - 1, Some(ex))
          }
        })
      } else {
        p.fail(ex.getOrElse(new Exception))
      }
    }

    rec(promise, 10, None)
    promise.future()
  }
}
