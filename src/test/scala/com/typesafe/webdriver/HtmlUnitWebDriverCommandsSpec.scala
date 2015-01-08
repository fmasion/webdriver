package com.typesafe.webdriver

import org.junit.runner.RunWith
import org.specs2.execute.{Result, AsResult}
import org.specs2.runner.JUnitRunner
import spray.json._
import scala.concurrent.{Await, Future}
import org.specs2.mutable.Specification
import org.specs2.time.NoDurationConversions
import org.specs2.matcher.MatchResult
import com.typesafe.webdriver.WebDriverCommands.{WebDriverErrorDetails, Errors, WebDriverError}

@RunWith(classOf[JUnitRunner])
class HtmlUnitWebDriverCommandsSpec extends Specification with NoDurationConversions {

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._

  def withSession[T](block: (WebDriverCommands, (String, Either[WebDriverError, JsValue])) => Future[Either[WebDriverError, T]]): Future[Either[WebDriverError, T]] = {
    val commands = new HtmlUnitWebDriverCommands()
    val maybeSession = commands.createSession().map { case (sessionId, createResult) =>
        val result = block(commands, (sessionId, createResult))
        result.onComplete {
          case _ => commands.destroySession(sessionId)
        }
        result
    }
    maybeSession.flatMap(x => x)
  }

  def testFor(v: JsValue): MatchResult[Any] = {
    val result = withSession {
      (commands, sessionId) =>
        val (id, _) = sessionId
        commands.executeJs(id, "var result = arguments[0];", JsArray(v))
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
      val maybeSession = commands.createSession().map {
        sessionId =>
          val (id, _) = sessionId
          commands.executeJs(id, "var result = arguments[0];", JsArray(JsNumber(1)))
            .flatMap {
            r =>
              import DefaultJsonProtocol._
              val result = commands.executeJs(
                id,
                "var result = arguments[0];",
                JsArray(JsNumber(2)))
              result.onComplete {
                case _ =>
                  commands.destroySession(id)
              }
              result
          }
      }

      val result = maybeSession.flatMap(x => x)
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
      (commands, sessionId) =>
        val (id, _) = sessionId
        commands.executeJs(id, "this is rubbish js;", JsArray())
    }
    Await.result(result, Duration(1, SECONDS)) must beLeft
  }

  "Execute JS natively requesting a commonjs function should fail" in {
    val result = withSession {
      (commands, sessionId) =>
        val (id, _) = sessionId
        commands.executeNativeJs(id, "var result = require('fs').separator;", JsArray())
    }
    Await.result(result, Duration(1, SECONDS)) must beLeft
  }

  "Navigate to a new page should work" in new WithStubServer {
    val baseUrl = s"http://localhost:$port/"
    val result = withSession {
      (commands, sessionId) =>
        val (id, _) = sessionId
        commands.navigateTo(id, baseUrl)
    }
    Await.result(result, Duration(10, SECONDS)) must beRight(())
  }
}
