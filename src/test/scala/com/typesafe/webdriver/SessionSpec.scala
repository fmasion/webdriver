package com.typesafe.webdriver

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.Specification
import scala.concurrent.{Await, Promise, Future}
import scala.concurrent.duration._
import org.specs2.time.NoTimeConversions
import spray.json.{JsObject, JsString, JsValue, JsArray}
import com.typesafe.webdriver.WebDriverCommands.WebDriverError

@RunWith(classOf[JUnitRunner])
class SessionSpec extends Specification with NoTimeConversions {

  class TestWebDriverCommands extends WebDriverCommands {
    val p = Promise[String]()
    val f = p.future

    def createSession(desiredCapabilities:JsObject=JsObject(), requiredCapabilities:JsObject=JsObject()): Future[String] = f

    def destroySession(sessionId: String) {}

    def executeJs(sessionId: String, script: String, args: JsArray): Future[Either[WebDriverError, JsValue]] =
      Future.successful(Right(JsString("hi")))

    override def executeNativeJs(sessionId: String, script: String, args: JsArray): Future[Either[WebDriverError, JsValue]] = {
      throw new UnsupportedOperationException
    }
  }

  "A session" should {
    "requeue requests while in a connecting state" in new TestActorSystem {

      val wd = new TestWebDriverCommands

      val session = system.actorOf(Session.props(wd))

      session ! Session.Connect()

      session ! Session.ExecuteJs("", JsArray())

      wd.p.success("123")

      Await.ready(wd.f, 2.seconds)

      expectMsg(JsString("hi"))

    }
  }
}
