import Dependencies._

ThisBuild / scalaVersion := "2.13.12"
ThisBuild / version      := "0.1.0"
ThisBuild / organization := "com.example"

Compile / packageBin / mainClass := Some("csw.proto.galil.hcd.GalilHcdApp")

lazy val aggregatedProjects: Seq[ProjectReference] = Seq(
  `galil-assembly`,
  `galil-hcd`,
  `galil-client`,
  `galil-simulator`,
  `galil-repl`,
  `galil-io`,
  `galil-deploy`
)

lazy val `galil-root` = project
  .in(file("."))
  .aggregate(aggregatedProjects: _*)

// The Galil prototype HCD
lazy val `galil-hcd` = project
  .enablePlugins(DeployApp)
  .settings(
    libraryDependencies ++= GalilHcd
  )
  .dependsOn(`galil-io`)

// The Galil prototype assembly
lazy val `galil-assembly` = project
  .enablePlugins(DeployApp)
  .settings(
    libraryDependencies ++= GalilAssembly
  )

// Scala client
lazy val `galil-client` = project
  .enablePlugins(DeployApp)
  .settings(libraryDependencies ++= GalilClient)
  .dependsOn(`galil-io`, `galil-simulator`, `galil-hcd` % "test->test")

// Galil simulator
lazy val `galil-simulator` = project
  .enablePlugins(DeployApp)
  .settings(libraryDependencies ++= GalilSimulator)
  .dependsOn(`galil-io`)

// REPL client
lazy val `galil-repl` = project
  .enablePlugins(DeployApp)
  .settings(libraryDependencies ++= GalilRepl)
  .dependsOn(`galil-io`)

// Galil IO module
lazy val `galil-io` = project
  .settings(libraryDependencies ++= GalilIo)

// Deploy module
lazy val `galil-deploy` = project
  .enablePlugins(DeployApp)
  .dependsOn(`galil-assembly`, `galil-hcd`)
  .settings(
    libraryDependencies ++= GalilDeploy
  )
