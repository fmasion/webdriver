package com.typesafe.webdriver.sbt

import sbt._
import sbt.Keys._
import akka.actor.ActorRef
import akka.pattern.gracefulStop
import com.typesafe.webdriver.{HtmlUnit, LocalBrowser, PhantomJs}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import com.typesafe.sbt.web.WebPlugin

/**
 * Declares the main parts of a WebDriver based plugin for sbt.
 */
object WebDriverPlugin extends sbt.Plugin {

  object WebDriverKeys {

    object BrowserType extends Enumeration {
      val HtmlUnit, PhantomJs = Value
    }

    val browserType = SettingKey[BrowserType.Value]("wd-browser-type", "The type of browser to use.")
    val webBrowser = TaskKey[ActorRef]("wd-web-browser", "An actor representing the webdriver browser.")
    val parallelism = SettingKey[Int]("wd-parallelism", "The number of parallel tasks for the webdriver host. Defaults to the # of available processors + 1 to keep things busy.")
  }

  import WebPlugin._
  import WebDriverKeys._

  private val browserAttrKey = AttributeKey[ActorRef]("web-browser")

  private def load(browserType: BrowserType.Value, state: State): State = {
    withActorRefFactory(state, WebDriverPlugin.getClass.getName) {
      arf =>
        val sessionProps = browserType match {
          case BrowserType.HtmlUnit => HtmlUnit.props()
          case BrowserType.PhantomJs => PhantomJs.props(arf)
        }
        val browser = arf.actorOf(sessionProps, "localBrowser")
        browser ! LocalBrowser.Startup
        val newState = state.put(browserAttrKey, browser)
        newState.addExitHook(unload(newState))
    }
  }

  private def unload(state: State): State = {
    state.get(browserAttrKey).foreach {
      browser =>
        try {
          val stopped: Future[Boolean] = gracefulStop(browser, 250.millis)
          Await.result(stopped, 500.millis)
        } catch {
          case _: Throwable =>
        }
    }
    state.remove(browserAttrKey)
  }

  override def globalSettings: Seq[Setting[_]] = super.globalSettings ++ Seq(
    onLoad in Global := (onLoad in Global).value andThen (load(browserType.value, _)),
    onUnload in Global := (onUnload in Global).value andThen (unload),
    browserType := BrowserType.HtmlUnit,
    webBrowser <<= state map (_.get(browserAttrKey).get),
    parallelism := java.lang.Runtime.getRuntime.availableProcessors() + 1
  )
}
