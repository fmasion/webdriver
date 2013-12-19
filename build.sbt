organization := "com.typesafe"

name := "webdriver"

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.2.3",
  "io.spray" % "spray-client" % "1.2.0",
  "io.spray" %% "spray-json" % "1.2.5",
  "net.sourceforge.htmlunit" % "htmlunit" % "2.13",
  "org.specs2" %% "specs2" % "2.2.2" % "test",
  "junit" % "junit" % "4.11" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.3" % "test"
)

publishTo := {
    val isSnapshot = version.value.contains("-SNAPSHOT")
    val typesafe = "http://private-repo.typesafe.com/typesafe/"
    val (name, url) = if (isSnapshot)
                        ("sbt-plugin-snapshots", typesafe + "maven-snapshots")
                      else
                        ("sbt-plugin-releases", typesafe + "maven-releases")
    Some(Resolver.url(name, new URL(url)))
}

lazy val root = project.in( file(".") )

lazy val `sbt-webdriver` = project.dependsOn(root)

lazy val `webdriver-tester` = project.dependsOn(root)
