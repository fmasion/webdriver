package com.typesafe.webdriver

import com.typesafe.webdriver.Session.SessionAborted
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.Specification
import scala.concurrent.{Await, Promise, Future}
import scala.concurrent.duration._
import org.specs2.time.NoTimeConversions
import spray.json.{JsObject, JsString, JsValue, JsArray}
import com.typesafe.webdriver.WebDriverCommands.{WebDriverSession, WebDriverErrorDetails, WebDriverError}

@RunWith(classOf[JUnitRunner])
class SessionSpec extends Specification with NoTimeConversions {

  class TestWebDriverCommands extends WebDriverCommands {
    val p = Promise[Either[WebDriverError, WebDriverSession]]()
    val f = p.future

    def createSession(desiredCapabilities:JsObject=JsObject(), requiredCapabilities:JsObject=JsObject()): Future[Either[WebDriverError, WebDriverSession]] = f

    def destroySession(sessionId: String) {}

    def executeJs(sessionId: String, script: String, args: JsArray): Future[Either[WebDriverError, JsValue]] =
      Future.successful(Right(JsString("hi")))

    override def executeNativeJs(sessionId: String, script: String, args: JsArray): Future[Either[WebDriverError, JsValue]] = {
      throw new UnsupportedOperationException
    }

    override def screenshot(sessionId: String): Future[Either[WebDriverError, JsValue]] = {
      Future.successful(Right(JsString("base64EncodedPNG")))
    }

    override def navigateTo(sessionId: String, url: String): Future[Either[WebDriverError, Unit]] =
      Future.successful(Right(Unit))
  }

  "A session" should {
    "tell its creator that it couldn't acquire the required capabilites" in new TestActorSystem {
      val wd = new TestWebDriverCommands
      val error = WebDriverError(-1, WebDriverErrorDetails("error"))
      val sid = "123"

      val session = system.actorOf(Session.props(wd))

      session ! Session.Connect()

      wd.p.success(Left(error))

      Await.ready(wd.f, 2.seconds)

      expectMsg(SessionAborted(error))
    }
    "requeue requests while in a connecting state" in new TestActorSystem {

      val wd = new TestWebDriverCommands

      val session = system.actorOf(Session.props(wd))

      session ! Session.Connect()

      session ! Session.ExecuteJs("", JsArray())

      wd.p.success(Right(WebDriverSession("123",JsObject())))

      Await.ready(wd.f, 2.seconds)

      expectMsg(JsString("hi"))

    }

    "capture a screenshot and return the base64 encoded string representing the PNG to the client" in new TestActorSystem {

      val wd = new TestWebDriverCommands

      val session = system.actorOf(Session.props(wd))

      session ! Session.Connect()

      wd.p.success(Right(WebDriverSession("123", JsObject())))
      Await.ready(wd.f, 2.seconds)

      session ! Session.ScreenShot

      expectMsg(JsString("base64EncodedPNG"))
    }

    "be able to navigate to an url" in new TestActorSystem {

      val wd = new TestWebDriverCommands

      val session = system.actorOf(Session.props(wd))

      session ! Session.Connect()

      session ! Session.NavigateTo("http://www.example.com")

      wd.p.success(Right(WebDriverSession("123", JsObject())))

      Await.ready(wd.f, 2.seconds)

      expectMsg(Session.Done)

    }
  }
}
