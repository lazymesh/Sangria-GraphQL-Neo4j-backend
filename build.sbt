name := "howtographql-sangria"

version := "1.0"

description := "GraphQL server with akka-http and sangria"

scalaVersion := "2.12.3"

scalacOptions ++= Seq("-deprecation", "-feature")

libraryDependencies ++= Seq(
  "org.sangria-graphql" %% "sangria" % "1.3.0",
  "org.sangria-graphql" %% "sangria-spray-json" % "1.0.0",
  "com.typesafe.akka" %% "akka-http" % "10.0.10",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.10",

  "org.neo4j.driver" % "neo4j-java-driver" % "1.6.1",
  "io.monix" %% "monix" % "3.0.0-RC1",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",

  "org.scalatest" %% "scalatest" % "3.0.4" % Test
)

Revolver.settings
