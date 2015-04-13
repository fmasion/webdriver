package com.typesafe.webdriver

import com.gargoylesoftware.htmlunit.{BrowserVersion, WebClient}
import scala.collection.concurrent.TrieMap
import java.util.UUID
import com.gargoylesoftware.htmlunit.html.HtmlPage
import scala.concurrent.Future
import spray.json._
import com.typesafe.webdriver.WebDriverCommands.{WebDriverSession, WebDriverErrorDetails, Errors, WebDriverError}

/**
 * Runs WebDriver command in the context of the JVM ala HtmlUnit.
 */
class HtmlUnitWebDriverCommands() extends WebDriverCommands {
  val sessions = TrieMap[String, HtmlPage]()

  import scala.concurrent.ExecutionContext.Implicits.global


  override def createSession(desiredCapabilities: JsObject = JsObject(),
                             requiredCapabilities: JsObject = JsObject()): Future[Either[WebDriverError, WebDriverSession]] = {
    // We like Chrome for no particular reason than its JS is modern. FF may also be a good choice.
    val webClient = new WebClient(BrowserVersion.CHROME)
    val page: HtmlPage = webClient.getPage(WebClient.ABOUT_BLANK)
    val sessionId = UUID.randomUUID().toString
    sessions.put(sessionId, page)
    Future.successful( Right(WebDriverSession(sessionId, JsObject())))
  }

  override def destroySession(sessionId: String): Unit = {
    sessions.remove(sessionId).foreach(_.getWebClient.closeAllWindows())
  }

  override def executeJs(sessionId: String, script: String, args: JsArray): Future[Either[WebDriverError, JsValue]] = {
    sessions.get(sessionId).map({
      page =>
        Future {
          val escapedJsonArgs = args.toString().replaceAll("'", "\\'").replaceAll("""\\""", """\\\\""")
          val scriptWithArgs = s"""|var arguments = JSON.parse('$escapedJsonArgs');
                                   |$script
                                   |result;
                                   |""".stripMargin
          try {
            val scriptResult = page.executeJavaScript(scriptWithArgs)
            def toJsValue(v: Any): JsValue = {
              import scala.collection.JavaConverters._
              v match {
                case b: java.lang.Boolean => JsBoolean(b)
                case n: Number => JsNumber(n.doubleValue())
                case s: String => JsString(s)
                case n if n == null => JsNull
                case l: java.util.List[_] => JsArray(l.asScala.toList.map(toJsValue): _*)
                case o: java.util.Map[_, _] => JsObject(o.asScala.map(p => p._1.toString -> toJsValue(p._2)).toMap)
                case x => JsString(x.toString)
              }
            }
            Right(toJsValue(scriptResult.getJavaScriptResult))
          } catch {
            case e: Exception =>
              Left(WebDriverError(
                Errors.JavaScriptError,
                WebDriverErrorDetails(e.getLocalizedMessage, stackTrace = Some(e.getStackTrace))))
          }
        }
    }).getOrElse(Future.successful(
      Left(WebDriverError(Errors.NoSuchDriver, WebDriverErrorDetails("Cannot locate sessionId")))))
  }

  override def executeNativeJs(sessionId: String, script: String, args: JsArray): Future[Either[WebDriverError, JsValue]] = {
    Future.successful(Left(WebDriverError(Errors.UnknownError, WebDriverErrorDetails("Unsupported operation"))))
  }

  override def screenshot(sessionId: String): Future[Either[WebDriverError, JsValue]] = {
    Future.successful(Left(WebDriverError(Errors.UnknownError, WebDriverErrorDetails("Unsupported operation"))))
  }

  override def navigateTo(sessionId: String, url: String): Future[Either[WebDriverError, Unit]] = {
    val webClient = new WebClient(BrowserVersion.CHROME)
    val navigationResult: Either[WebDriverError, Unit] = try {
      sessions.put(sessionId, webClient.getPage(url))
      Right(())
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        Left(WebDriverError(Errors.UnknownError, WebDriverErrorDetails(t.getMessage)))
    }
    Future.successful(navigationResult)
  }
}
