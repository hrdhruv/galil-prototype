import java.io.FileReader
import java.util.Properties
import sbt._
import scala.util.control.NonFatal

object Libs {
  // ✅ Scala version consistent with CSW 5.0.1
  val ScalaVersion: String = "2.13.12"

  val `scopt` = "com.github.scopt" %% "scopt" % "4.1.0"
  val `slf4j-api` = "org.slf4j" % "slf4j-api" % "2.0.17"
  val playJson = "com.typesafe.play" %% "play-json" % "2.9.4" // ✅ Works for Scala 2.13

  val `scalatest`       = "org.scalatest" %% "scalatest" % "3.2.19"
  val `mockito-scala`   = "org.mockito" %% "mockito-scala" % "1.17.27"
}

object CSW {
  val Version: String = {
    var reader: FileReader = null
    try {
      val properties = new Properties()
      reader = new FileReader("project/build.properties")
      properties.load(reader)
      val version = properties.getProperty("csw.version")
      println(s"[info] Using CSW version [$version] ***********")
      version
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
        throw e
    } finally if (reader != null) reader.close()
  }

  val `csw-framework` = "com.github.tmtsoftware.csw" %% "csw-framework" % Version
  val `csw-testkit`   = "com.github.tmtsoftware.csw" %% "csw-testkit" % Version
}
