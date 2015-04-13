package com.typesafe.webdriver

import akka.actor.{ActorRef, FSM, Props, Actor}
import com.typesafe.webdriver.Session._
import scala.Some
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import spray.can.Http.ConnectionAttemptFailedException
import spray.json.{JsObject, JsValue, JsArray}
import com.typesafe.webdriver.WebDriverCommands.{WebDriverSession, WebDriverError}
import scala.concurrent.Future

/**
 * Browsers maintain sessions for the purposes of our interactions with them. Sessions can be requested to do things
 * such as create a document or execute some JavaScript.
 * @param wd the commands to use for communicating with a web driver host.
 * @param sessionConnectTimeout the timeout value for waiting on session establishment.
 */
class Session(wd: WebDriverCommands, sessionConnectTimeout: FiniteDuration)
  extends Actor
  with FSM[State, Option[String]] {

  startWith(Uninitialized, None)

  when(Uninitialized) {
    case Event(Connect(desiredCapabilities, requiredCapabilities), None) =>
      val origSender = sender()
      wd.createSession(desiredCapabilities,requiredCapabilities).onComplete {
        case Success(Right(WebDriverSession(sessionId, capabilities))) =>
          self.tell(SessionCreated(sessionId, capabilities), origSender)
        case Success(Left(error)) =>
          log.error("Stopping due to not being able to establish a session with requested capabilities - web driver error - {}.",error)
          origSender.tell(SessionAborted(error), origSender)
          stop()
        case Failure(_: ConnectionAttemptFailedException) =>
          log.debug("Initial connection attempt failed - retrying shortly.")
          context.system.scheduler.scheduleOnce(500.milliseconds) {
            self.tell(Connect(desiredCapabilities, requiredCapabilities), origSender)
          }
        case Failure(t) =>
          log.error("Stopping due to not being able to establish a session - exception thrown - {}.", t)
          stop()
      }
      goto(Connecting) using None
  }

  when(Connecting, stateTimeout = sessionConnectTimeout) {
    case Event(Connect(desiredCapabilities, requiredCapabilities), None) =>
      val origSender = sender()
      wd.createSession(desiredCapabilities, requiredCapabilities).onComplete {
        case Success(Right(WebDriverSession(sessionId, capabilities))) => self.tell(SessionCreated(sessionId, capabilities), origSender)
        case Success(Left(error)) =>
          log.error("Stopping due to not being able to establish a session with requested capabilities - web driver error - {}.",error)
          origSender.tell(SessionAborted(error), origSender)
          stop()
        case Failure(t) =>
          log.error("Stopping due to not being able to establish a session - exception thrown - {}.", t)
          stop()
      }
      stay()
    case Event(SessionCreated(sessionId, capabilities), None) =>
      goto(Connected) using Some(sessionId)
    case Event(StateTimeout, None) =>
      log.error("Stopping due to not being able to establish a session - timed out.")
      stop()
    case Event(e: ExecuteJs, None) =>
      self forward e
      stay()
    case Event(e: ExecuteNativeJs, None) =>
      self forward e
      stay()
    case evt@Event(ScreenShot, None) =>
      self forward evt.event
      stay()
  }

  private def handleJsExecuteResult(maybeResult: Future[Either[WebDriverError, JsValue]], origSender: ActorRef): Unit = {
    maybeResult.onComplete {
      case Success(Right(result)) => origSender ! result
      case Success(Left(error)) => origSender ! akka.actor.Status.Failure(error.toException)
      case Failure(t) =>
        origSender ! akka.actor.Status.Failure(t)
        log.error("Stopping due to a problem executing commands - {}.", t)
        stop()
    }
  }

  when(Connected) {
    case Event(e: ExecuteJs, someSessionId@Some(_)) => {
      val origSender = sender()
      someSessionId.foreach {
        sessionId =>
          handleJsExecuteResult(wd.executeJs(sessionId, e.script, e.args), origSender)
      }
      stay()
    }
    case Event(e: ExecuteNativeJs, someSessionId@Some(_)) => {
      val origSender = sender()
      someSessionId.foreach {
        sessionId =>
          handleJsExecuteResult(wd.executeNativeJs(sessionId, e.script, e.args), origSender)
      }
      stay()
    }
    case Event(ScreenShot, someSessionId@Some(_)) => {
      val origSender = sender()
      someSessionId.foreach {
        sessionId =>
          handleJsExecuteResult(wd.screenshot(sessionId), origSender)
      }
      stay()
    }
  }

  onTermination {
    case StopEvent(_, _, someSessionId@Some(_)) => someSessionId.foreach(wd.destroySession)
  }

  initialize()
}

object Session {

  /**
   * Connect a session.
   */
  case class Connect(desiredCapabilities: JsObject = JsObject(), requiredCapabilities: JsObject = JsObject())

  /**
   * Execute JavaScript.
   * @param script the js to execute.
   * @param args the arguments to pass to the script.
   */
  case class ExecuteJs(script: String, args: JsArray)

  /**
   * Execute JavaScript natively i.e. with access to the features of the underlying browser and/or
   * JavaScript engine. In the case of PhantomJs, for example, the file system is available along
   * with the CommonJs/File library.
   * @param script the js to execute.
   * @param args the arguments to pass to the script.
   */
  case class ExecuteNativeJs(script: String, args: JsArray)

  case class SessionAborted(error:WebDriverError)

  /**
   * Take a screenshot
   */
  case object ScreenShot

  /**
   * A convenience for creating the actor.
   */
  def props(wd: WebDriverCommands, sessionConnectTimeout: FiniteDuration = 2.seconds): Props =
    Props(classOf[Session], wd, sessionConnectTimeout)


  // Internal messages

  private[webdriver] case class SessionCreated(sessionId: String, capabilities:JsValue)


  // Internal FSM states

  private[webdriver] trait State

  private[webdriver] case object Uninitialized extends State

  private[webdriver] case object Connecting extends State

  private[webdriver] case object Connected extends State

}
