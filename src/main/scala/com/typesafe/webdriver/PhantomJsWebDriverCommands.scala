package com.typesafe.webdriver

import spray.json.{JsString, JsValue, JsArray}
import scala.concurrent.Future
import com.typesafe.webdriver.WebDriverCommands.WebDriverError
import akka.actor.ActorRefFactory
import spray.client.pipelining._

/**
 * Specialisations for PhantomJs.
 */
class PhantomJsWebDriverCommands(arf: ActorRefFactory, host: String, port: Int)
  extends HttpWebDriverCommands(arf, host, port) {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def executeNativeJs(sessionId: String, script: String, args: JsArray): Future[Either[WebDriverError, JsValue]] = {
    pipeline(Post(s"/session/$sessionId/phantom/execute", s"""{"script":${JsString(s"$script;return result;")},"args":$args}"""))
      .map(toEitherErrorOrValue)
  }
}
