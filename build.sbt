name := "trololo-server"
organization := "com.jonathanmorabito"
version := "1.0"
scalaVersion := "2.11.11"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= {
  val akkaV       = "2.4.19"
  val akkaHttpV   = "10.0.8"
  val scalaTestV  = "3.0.1"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-testkit" % akkaV,
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV,
    "org.scalatest"     %% "scalatest" % scalaTestV % "test"
  )
}
