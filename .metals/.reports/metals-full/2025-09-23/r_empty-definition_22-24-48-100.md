error id: file://<WORKSPACE>/galil-hcd/src/main/scala/csw/proto/galil/hcd/CSWDeviceAdapter.scala:scala/Predef.String#
file://<WORKSPACE>/galil-hcd/src/main/scala/csw/proto/galil/hcd/CSWDeviceAdapter.scala
empty definition using pc, found symbol in pc: scala/Predef.String#
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -scala/jdk/CollectionConverters.String#
	 -String#
	 -scala/Predef.String#
offset: 546
uri: file://<WORKSPACE>/galil-hcd/src/main/scala/csw/proto/galil/hcd/CSWDeviceAdapter.scala
text:
```scala
package csw.proto.galil.hcd

import com.typesafe.config.Config
import csw.params.commands.CommandResponse.{Completed, SubmitResponse}
import csw.params.commands.{Result, Setup}
import csw.params.core.generics.{Key, KeyType, Parameter}
import csw.params.core.models.{Id, ObsId}

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object CSWDeviceAdapter {
  case class CommandMapEntry(name: String, command: String, responseFormat: String)
  private case class ParamDefEntry(name: St@@ring, typeStr: String, range: String, dataRegex: String)

  val axisKey: Key[Char]            = KeyType.CharKey.make("axis")
  val eDescKey: Key[String]         = KeyType.StringKey.make("eDesc")
  val mTypeKey: Key[Double]         = KeyType.DoubleKey.make("mType")
  val eCodeKey: Key[Int]            = KeyType.IntKey.make("eCode")
  val swStatusKey: Key[Int]          = KeyType.IntKey.make("swStatus")
  val lcParamKey: Key[Int]          = KeyType.IntKey.make("lcParam")
  val smoothKey: Key[Double]        = KeyType.DoubleKey.make("smooth")
  val speedKey: Key[Int]            = KeyType.IntKey.make("speed")
  val countsKey: Key[Int]           = KeyType.IntKey.make("counts")
  val interpCountsKey: Key[Int]     = KeyType.IntKey.make("interpCounts")
  val brushlessModulusKey: Key[Int] = KeyType.IntKey.make("brushlessModulus")
  val voltsKey: Key[Double]         = KeyType.DoubleKey.make("volts")

  private val commandParamKeys: List[Key[?]] = List(
    axisKey, eDescKey, mTypeKey, eCodeKey, swStatusKey, lcParamKey,
    smoothKey, speedKey, countsKey, interpCountsKey, brushlessModulusKey, voltsKey
  )

  private val commandParamKeyMap: Map[String, Key[?]] = commandParamKeys.map(k => k.keyName -> k).toMap
  private val paramRegex = raw"\(([A-Za-z]*)\)".r
}

class CSWDeviceAdapter(config: Config) {
  import CSWDeviceAdapter._

  private val cmdConfig = config.getConfig("commandMap")
  private val cmdNames  = cmdConfig.root.keySet().asScala.toList
  private val cmdMap = cmdNames.map { cmdName =>
    val cfg = cmdConfig.getConfig(cmdName)
    cmdName -> CommandMapEntry(cmdName, cfg.getString("command"), cfg.getString("responseFormat"))
  }.toMap

  private val paramDefConfig = config.getConfig("paramDefMap")
  private val paramDefNames  = paramDefConfig.root.keySet().asScala.toList
  private val paramDefMap = paramDefNames.map { paramDefName =>
    val cfg = paramDefConfig.getConfig(paramDefName)
    paramDefName -> ParamDefEntry(
      paramDefName,
      cfg.getString("type"),
      if (cfg.hasPath("range")) cfg.getString("range") else "",
      cfg.getString("dataRegex")
    )
  }.toMap

  def getCommandMapEntry(setup: Setup): Try[CommandMapEntry] =
    Try(cmdMap(setup.commandName.name))

  def validateSetup(setup: Setup, cmdEntry: CommandMapEntry): Try[String] = {
    val paramDefs = paramRegex.findAllMatchIn(cmdEntry.command).toList.map(_.group(1)).map(paramDefMap(_))
    val missing = paramDefs.flatMap { p =>
      val key = commandParamKeyMap(p.name)
      if (setup.contains(key)) None else Some(new RuntimeException(s"Missing ${key.keyName} parameter"))
    }

    if (missing.nonEmpty) Failure(missing.head)
    else Success(insertParams(setup, cmdEntry.command, paramDefs))
  }

  @tailrec
  private def insertParams(setup: Setup, cmd: String, paramDefs: List[ParamDefEntry]): String = paramDefs match {
    case h :: t =>
      val key   = commandParamKeyMap(h.name)
      val param = setup.get(key).get
      val s     = cmd.replace(s"(${key.keyName})", param.head.toString)
      insertParams(setup, s, t)
    case Nil => cmd
  }

  def makeResponse(runId: Id, maybeObsId: Option[ObsId], cmdEntry: CommandMapEntry, responseStr: String): SubmitResponse = {
    if (cmdEntry.responseFormat.isEmpty) Completed(runId)
    else {
      val paramDefs     = paramRegex.findAllMatchIn(cmdEntry.responseFormat).toList.map(_.group(1)).map(paramDefMap(_))
      val responseRegex = insertResponseRegex(cmdEntry.responseFormat, paramDefs)
      val paramValues   = responseRegex.r.findAllIn(responseStr).toList
      val resultParamSet = makeResultParamSet(paramValues, paramDefs, Nil).toSet
      Completed(runId, Result(resultParamSet))
    }
  }

  @tailrec
  private def insertResponseRegex(responseFormat: String, paramDefs: List[ParamDefEntry]): String = paramDefs match {
    case h :: t =>
      insertResponseRegex(responseFormat.replace(s"(${h.name})", h.dataRegex), t)
    case Nil => responseFormat
  }

  private def makeResultParamSet(
      paramValues: List[String],
      paramDefs: List[ParamDefEntry],
      paramSet: List[Parameter[?]]
  ): List[Parameter[?]] = {
    paramValues.zip(paramDefs).map { case (valueStr, defn) =>
      val key     = commandParamKeyMap(defn.name)
      defn.typeStr.trim match {
        case "char"   => key.asInstanceOf[Key[Char]].set(valueStr.charAt(0))
        case "string" => key.asInstanceOf[Key[String]].set(valueStr)
        case "double" => key.asInstanceOf[Key[Double]].set(valueStr.toDouble)
        case "int"    => key.asInstanceOf[Key[Int]].set(valueStr.toInt)
      }
    }
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: scala/Predef.String#