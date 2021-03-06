package com.typesafe.webdriver.tester

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask

import com.typesafe.webdriver.{Session, PhantomJs, LocalBrowser}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import spray.json._

object Main {
  def main(args: Array[String]) {
    implicit val system = ActorSystem("webdriver-system")
    implicit val timeout = Timeout(5.seconds)

    system.scheduler.scheduleOnce(7.seconds) {
      system.shutdown()
      System.exit(1)
    }

    val browser = system.actorOf(PhantomJs.props(system), "localBrowser")
    browser ! LocalBrowser.Startup
    for (
      session <- (browser ? LocalBrowser.CreateSession()).mapTo[ActorRef];
      result <- (session ? Session.ExecuteNativeJs("return arguments[0]", JsArray(JsNumber(999)))).mapTo[JsNumber]
    ) yield {
      println(result)

      try {
        system.shutdown()
        System.exit(0)
      } catch {
        case _: Throwable =>
      }

    }

  }
}
