import sbt._

object Dependencies {

  val cswVersion = "5.0.1"

  lazy val GalilHcd = Seq(
    "com.github.tmtsoftware.csw" %% "csw-framework" % cswVersion,
    "com.github.tmtsoftware.csw" %% "csw-testkit" % cswVersion % Test,
    "org.scalatest"               %% "scalatest"   % "3.2.17" % Test
  )

  lazy val GalilAssembly = Seq(
    "com.github.tmtsoftware.csw" %% "csw-framework" % cswVersion,
    "com.github.tmtsoftware.csw" %% "csw-testkit" % cswVersion % Test,
    "org.scalatest"               %% "scalatest"   % "3.2.17" % Test
  )

  lazy val GalilClient = Seq(
    "com.github.tmtsoftware.csw" %% "csw-framework" % cswVersion,
    "com.github.tmtsoftware.csw" %% "csw-testkit"  % cswVersion,
    "org.scalatest"               %% "scalatest"    % "3.2.17" % Test
  )

  lazy val GalilDeploy = Seq(
    "com.github.tmtsoftware.csw" %% "csw-framework" % cswVersion,
    "org.scalatest"               %% "scalatest"    % "3.2.17" % Test
  )

  lazy val GalilSimulator = Seq(
    "com.github.tmtsoftware.csw" %% "csw-framework" % cswVersion,
    "org.scalatest"               %% "scalatest"    % "3.2.17" % Test
  )

  lazy val GalilRepl = Seq(
    "com.github.tmtsoftware.csw" %% "csw-framework" % cswVersion
  )

  lazy val GalilIo = Seq(
    "com.github.tmtsoftware.csw" %% "csw-framework" % cswVersion,
    "com.typesafe.play" %% "play-json" % "2.10.0",
    "org.scalatest"              %% "scalatest"     % "3.2.17" % Test
  )

  lazy val GalilCommands = Seq(
    "com.github.tmtsoftware.csw" %% "csw-framework" % cswVersion,
    "org.scalatest"               %% "scalatest"   % "3.2.17" % Test
  )
}
