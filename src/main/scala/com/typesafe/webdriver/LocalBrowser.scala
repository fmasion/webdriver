package com.typesafe.webdriver

import akka.actor._
import akka.contrib.process.BlockingProcess
import akka.contrib.process.BlockingProcess.Exited
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.stream.io.Framing
import akka.stream.scaladsl.Source
import akka.util.ByteString
import spray.json.JsObject
import com.typesafe.webdriver.LocalBrowser._
import scala.collection.immutable

/**
 * Provides an Actor on behalf of a browser. Browsers are represented as operating system processes and are
 * communicated with by using the http/json based WebDriver protocol.
 * @param sessionProps the properties required in order to produce a session actor.
 * @param maybeArgs a sequence of command line arguments used to launch the browser from the command line. If this is
 *                  set to None then the process is deemed to be controlled outside of this actor.
 */
class LocalBrowser(sessionProps: Props, maybeArgs: Option[immutable.Seq[String]]) extends Actor with FSM[State, Option[ActorRef]] {

  val settings = ActorMaterializerSettings(context.system)
  implicit val materializer = ActorMaterializer(settings)

  startWith(Uninitialized, None)

  when(Uninitialized) {
    case Event(Startup, None) =>
      maybeArgs match {
        case Some(args) =>
          context.actorOf(BlockingProcess.props(args))
          stay()
        case None => goto(Started) using None
      }
    case Event(BlockingProcess.Started(stdin,stdout,stderr),_)=>
      Source(stdout).via(Framing.delimiter(ByteString("\n"), Int.MaxValue).map(_.utf8String).named("lineFraming")).runForeach(log.debug)
      Source(stderr).via(Framing.delimiter(ByteString("\n"), Int.MaxValue).map(_.utf8String).named("lineFraming")).runForeach(log.error)
      goto(Started) using Some(sender())
  }

  when(Started) {
    case Event(CreateSession(desiredCapabilities, requiredCapabilities), _) =>
      val session = context.actorOf(sessionProps, "session")
      session ! Session.Connect(desiredCapabilities, requiredCapabilities)
      sender ! session
      stay()
    case Event(Exited(exitCode), _)=>
      stop()
  }

  onTermination {
    case StopEvent(_, _, maybeProcess) =>
      maybeProcess.foreach(p => p ! BlockingProcess.Destroy)
  }

  initialize()
}




object LocalBrowser {

  /**
   * Start a browser. This is typically sent upon having obtained an actor ref to the browser.
   */
  case object Startup

  /**
   * Start a new session.
   */
  case class CreateSession(desiredCapabilities:JsObject=JsObject(), requiredCapabilities:JsObject=JsObject())


  // Internal FSM states

  private[webdriver] trait State

  private[webdriver] case object Uninitialized extends State

  private[webdriver] case object Started extends State

}

/**
 * Used to manage a local instance of PhantomJs. The default is to assume that phantomjs is on the path.
 */
object PhantomJs {
  def props(arf: ActorRefFactory, host: String = "127.0.0.1", port: Int = 8910, _args:Seq[String]=Seq.empty ): Props = {
    val wd = new PhantomJsWebDriverCommands(arf, host, port)
    val args = Some(Seq("phantomjs", s"--webdriver=$host:$port") ++ _args)
    Props(classOf[LocalBrowser], Session.props(wd), args)
  }
}

/**
 * Used to manage a JVM resident browser via HtmlUnit.
 */
object HtmlUnit {
  def props(): Props = {
    val wd = new HtmlUnitWebDriverCommands
    Props(classOf[LocalBrowser], Session.props(wd), None)
  }
}
