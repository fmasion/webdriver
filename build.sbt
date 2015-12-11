organization := "com.typesafe"
name := "webdriver"

scalaVersion := "2.10.4"

version := "1.1.2"

libraryDependencies ++= Seq(

  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.typesafe.akka" %% "akka-contrib-extra" % "1.18.0",
  "io.spray" %% "spray-client" % "1.3.2",
  "io.spray" %% "spray-json" % "1.3.2",
  "net.sourceforge.htmlunit" % "htmlunit" % "2.15",
  "org.specs2" %% "specs2-core" % "3.4" % "test",
  "junit" % "junit" % "4.11" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11" % "test"
)
// Required by specs2 to get scalaz-stream
resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

//Required by sbt even though it is included in activator
resolvers += "typesafe-bintray" at "https://repo.typesafe.com/typesafe/releases/"

lazy val root = project in file(".")

lazy val `webdriver-tester` = project.dependsOn(root)

// Publish settings
publishTo := Some("Fred's bintray" at "https://api.bintray.com/maven/fmasion/maven/webdriver")
publishMavenStyle := true

homepage := Some(url("https://github.com/typesafehub/webdriver"))
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html"))
pomExtra := {
  <scm>
    <url>git@github.com:typesafehub/webdriver.git</url>
    <connection>scm:git:git@github.com:typesafehub/webdriver.git</connection>
  </scm>
  <developers>
    <developer>
      <id>playframework</id>
      <name>Play Framework Team</name>
      <url>https://github.com/playframework</url>
    </developer>
  </developers>
}
pomIncludeRepository := { _ => false }

// Release settings
releaseSettings
ReleaseKeys.crossBuild := true
ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value
ReleaseKeys.tagName := (version in ThisBuild).value
