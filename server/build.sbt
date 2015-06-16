name := """server"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava, PlayEbean)

scalaVersion := "2.11.6"

resolvers += "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/"

libraryDependencies ++= Seq(
  javaJdbc,
  cache,
  javaWs,
  
  "org.hibernate" % "hibernate-entitymanager" % "4.3.10.Final",
  "org.avaje.ebeanorm" % "avaje-ebeanorm-api" % "3.1.1",
  "org.apache.derby" % "derby" % "10.11.1.1",
  "org.daisy.pipeline" % "clientlib-java" % "2.0.0",
  "org.apache.commons" % "commons-compress" % "1.9",
  "org.apache.commons" % "commons-email" % "1.4",
  "log4j" % "log4j" % "1.2.17",
  "log4j" % "apache-log4j-extras" % "1.2.17"
)

scalacOptions += "-deprecation"

fork in run := true