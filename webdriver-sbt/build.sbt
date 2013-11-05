sbtPlugin := true

organization := "com.typesafe"

name := "webdriver-sbt"

version := "1.0.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "com.typesafe" %% "webdriver" % "1.0.0-SNAPSHOT"
)

addSbtPlugin("com.typesafe" %% "js-sbt" % "1.0.0-SNAPSHOT")
