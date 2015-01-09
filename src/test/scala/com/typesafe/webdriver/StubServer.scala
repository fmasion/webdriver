package com.typesafe.webdriver

import java.net.ServerSocket

import akka.actor._
import akka.io.IO
import akka.util.Timeout
import akka.pattern.ask
import org.specs2.execute.{Result, AsResult}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import org.specs2.time.NoDurationConversions
import spray.can.Http
import spray.http._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class DefaultStubHandler extends Actor with ActorLogging {
  def receive = {
    case _: Http.Connected =>
      sender ! Http.Register(self)
    case HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _) =>
      sender() ! HttpResponse(entity = HttpEntity(MediaTypes.`text/html`, "ok"), status = 200)
    case evt: HttpRequest =>
      sender() ! HttpResponse(status = 404)
  }
}

abstract class WithStubServer(handlerProps: Props = Props[DefaultStubHandler]) extends Around with Scope with NoDurationConversions {
  lazy val port = StubServer.randomPort

  override def around[T: AsResult](t: => T): Result = {
    implicit val system = ActorSystem()
    implicit val timeout = Timeout(Duration(10, MINUTES))
    val handler = system.actorOf(StubServer.props(port, handlerProps), name = "server")

    val eventualStarted = handler ? StubServer.Start
    val eventualResult = eventualStarted.map { _ =>
      val effectiveResult = AsResult.effectively(t)
      handler ! StubServer.Stop
      system.awaitTermination()
      effectiveResult
    }

    Await.result(eventualResult, Duration(10, MINUTES))
  }
}

class StubServer(port: Int, handlerProps: Props) extends Actor with Stash with ActorLogging {
  implicit val system = context.system
  private val handler = system.actorOf(handlerProps, name = "handler")

  def receive: Receive = {
    case StubServer.Start =>
      log.info(s"starting stub http server on port $port")
      IO(Http) ! Http.Bind(handler, interface = "localhost", port = this.port)
      context.become(starting(sender()), discardOld = true)
  }

  def starting(client: ActorRef): Receive = {
    case Http.Bound(addr) =>
      context.become(running(sender()), discardOld = true)
      unstashAll()
      client !()
    case _ => stash()
  }

  def running(listener: ActorRef): Receive = {
    case StubServer.Stop =>
      listener ! Http.Unbind
      context.become(stopping, discardOld = true)
    case _ => ()
  }

  def stopping: Receive = {
    case Http.Unbound =>
      system.shutdown()
  }
}

object StubServer {
  def randomPort: Int = {
    val s = new ServerSocket(0)
    val port = s.getLocalPort
    s.close()
    if (port == 0) randomPort else port
  }

  def props(port: Int, handlerProps: Props) = Props(new StubServer(port, handlerProps))

  case object Stop

  case object Start

}
