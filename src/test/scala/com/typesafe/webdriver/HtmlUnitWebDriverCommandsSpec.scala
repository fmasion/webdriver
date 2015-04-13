package com.typesafe.webdriver

import org.junit.runner.RunWith
import org.specs2.execute.{Result, AsResult}
import org.specs2.runner.JUnitRunner
import spray.json._
import scala.concurrent.{Await, Future}
import org.specs2.mutable.Specification
import org.specs2.time.NoDurationConversions
import org.specs2.matcher.MatchResult
import com.typesafe.webdriver.WebDriverCommands.{WebDriverSession, WebDriverErrorDetails, Errors, WebDriverError}
import java.io.File

@RunWith(classOf[JUnitRunner])
class HtmlUnitWebDriverCommandsSpec extends Specification with NoDurationConversions {

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._

  def withSession[T](block: (WebDriverCommands, WebDriverSession ) => Future[Either[WebDriverError, T]]): Future[Either[WebDriverError, T]] = {
    val commands = new HtmlUnitWebDriverCommands()
    val maybeSession:Future[Either[WebDriverError, T]] = commands.createSession().flatMap {
      case Right(session) =>
        val result = block(commands, session)
        result.onComplete {
          case _ => commands.destroySession(session.id)
        }
        result
      case Left(error) => Future.successful(Left(error))
    }
    maybeSession
  }

  def testFor(v: JsValue): MatchResult[Any] = {
    val result = withSession {
      (commands, session) =>
        commands.executeJs(session.id, "var result = arguments[0];", JsArray(v))
    }
    Await.result(result, Duration(1, SECONDS)) must_== Right(v)
  }

  "HtmlUnit" should {
    "execute js returning a boolean" in testFor(JsTrue)
    "execute js returning a number" in testFor(JsNumber(1))
    "execute js returning a string" in testFor(JsString("hi"))
    "execute js returning a null" in testFor(JsNull)
    "execute js returning an array" in testFor(JsArray(JsNumber(1)))
    "execute js returning an object" in testFor(JsObject("k" -> JsString("v")))

    "execute 2 js scripts for the same session" in {
      val commands = new HtmlUnitWebDriverCommands()
      val result = commands.createSession().flatMap{
        case Right(session) =>
          commands.executeJs(session.id, "var result = arguments[0];", JsArray(JsNumber(1)))
            .flatMap {
            r =>
              import DefaultJsonProtocol._
              val result = commands.executeJs(
                session.id,
                "var result = arguments[0];",
                JsArray(JsNumber(2)))
              result.onComplete {
                case _ =>
                  commands.destroySession(session.id)
              }
              result
          }
        case Left(error) => Future.successful(Left(error))
      }

      Await.result(result, Duration(1, SECONDS)) must_== Right(JsNumber(2))
    }
  }

  "should fail executing js without a session" in {
    val commands = new HtmlUnitWebDriverCommands()
    val result = commands.executeJs("rubbish", "var result = arguments[0];", JsArray(JsNumber(1)))
    Await.result(result, Duration(1, SECONDS)) must_==
      Left(WebDriverError(Errors.NoSuchDriver, WebDriverErrorDetails("Cannot locate sessionId")))
  }

  "should fail to execute invalid javascript" in {
    val result = withSession {
      (commands, session) =>
        commands.executeJs(session.id, "this is rubbish js;", JsArray())
    }
    Await.result(result, Duration(1, SECONDS)) must beLeft
  }

  "Execute JS natively requesting a commonjs function should fail" in {
    val result = withSession {
      (commands, session) =>
        commands.executeNativeJs(session.id, "var result = require('fs').separator;", JsArray())
    }
    Await.result(result, Duration(1, SECONDS)) must beLeft
  }

  "Navigate to a new page should work" in new WithStubServer {
    val baseUrl = s"http://localhost:$port/"
    val result = withSession {
      (commands, session) =>
        commands.navigateTo(session.id, baseUrl)
    }
    Await.result(result, Duration(10, SECONDS)) must beRight(())
  }
}
