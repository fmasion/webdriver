sbtPlugin := true

organization := "com.typesafe"

name := "sbt-webdriver"

version := "1.0.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "com.typesafe" %% "webdriver" % "1.0.0-SNAPSHOT"
)

addSbtPlugin("com.typesafe" %% "sbt-web" % "1.0.0-SNAPSHOT")
