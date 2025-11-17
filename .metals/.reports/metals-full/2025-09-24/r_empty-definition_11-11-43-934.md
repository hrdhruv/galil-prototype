error id: file://<WORKSPACE>/galil-hcd/src/main/scala/csw/proto/galil/hcd/GalilHcd.scala:Subsystem.
file://<WORKSPACE>/galil-hcd/src/main/scala/csw/proto/galil/hcd/GalilHcd.scala
empty definition using pc, found symbol in pc: Subsystem.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -GalilInfo.csw.prefix.models.Subsystem.
	 -csw/prefix/models/Subsystem.
	 -scala/Predef.csw.prefix.models.Subsystem.
offset: 691
uri: file://<WORKSPACE>/galil-hcd/src/main/scala/csw/proto/galil/hcd/GalilHcd.scala
text:
```scala
package csw.proto.galil.hcd

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import com.typesafe.config.ConfigFactory
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.deploy.containercmd.ContainerCmd
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse.{SubmitResponse, ValidateCommandResponse, Accepted, Invalid, Completed, Started, Error}
import csw.params.commands.{ControlCommand, Setup, Observe, CommandIssue}
import csw.params.core.models.{Id, ObsId}
import csw.prefix.models.Subs@@ystem.CSW
import csw.time.core.models.UTCTime
import csw.params.core.generics.{KeyType, Key, Parameter}
import csw.params.events.{SystemEvent, EventName}

import scala.concurrent.ExecutionContextExecutor

// Import GalilInfo
import GalilInfo._

// GalilInfo object to hold HCD state
object GalilInfo {
  val currentPositionKey: Key[Double] = KeyType.DoubleKey.make("currentPosition")
  var currentPosition: Parameter[Double] = Parameter(currentPositionKey, 0.0)

  val targetPositionKey: Key[Double] = KeyType.DoubleKey.make("targetPosition")
  val stageKey: Key[String] = KeyType.StringKey.make("stage")
  val statusKey: Key[String] = KeyType.StringKey.make("status")
  val messageKey: Key[String] = KeyType.StringKey.make("message")
}

class GalilHcdHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext)
    extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._

  private val log                           = loggerFactory.getLogger
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val config                        = ConfigFactory.load("GalilCommands.conf")
  private val adapter                       = new CSWDeviceAdapter(config)

  private val galilIoActor: ActorRef[GalilCommandMessage] =
    ctx.spawn(
      GalilIOActor.behavior(
        getGalilConfig,
        commandResponseManager,
        adapter,
        loggerFactory,
        componentInfo.prefix,
        cswCtx.currentStatePublisher
      ),
      "GalilIOActor"
    )

  override def initialize(): Unit = {
    log.info(s"Initializing ${componentInfo.prefix}")
  }

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = {
    controlCommand match {
      case setup: Setup =>
        val cmdMapEntryTry = adapter.getCommandMapEntry(setup)
        cmdMapEntryTry match {
          case scala.util.Success(cmdMapEntry) =>
            val cmdStringTry = adapter.validateSetup(setup, cmdMapEntry)
            cmdStringTry match {
              case scala.util.Success(_) => Accepted(runId)
              case scala.util.Failure(ex) =>
                Invalid(runId, CommandIssue.ParameterValueOutOfRangeIssue(ex.getMessage))
            }
          case scala.util.Failure(ex) =>
            Invalid(runId, CommandIssue.OtherIssue(ex.getMessage))
        }
      case _: Observe =>
        Invalid(runId, CommandIssue.UnsupportedCommandIssue("Observe not supported"))
    }
  }

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    log.info(s"GalilHcd: Handling command with runId - $runId")
    controlCommand match {
      case setup: Setup =>
        val cmdMapEntry = adapter.getCommandMapEntry(setup).get
        val cmdString   = adapter.validateSetup(setup, cmdMapEntry).get
        galilIoActor ! GalilRequest(cmdString, runId, setup.maybeObsId, cmdMapEntry)
        Started(runId)

      case _ =>
        Error(runId, "Unexpected submit command")
    }
  }

  // Example of command execution similar to old PactHCD logic
  private def moveToTarget(target: Double): Unit = {
    val delayMs = 1000

    while (currentPosition.head != target) {
      val newPos = if (currentPosition.head < target) currentPosition.head + 50 else currentPosition.head - 50
      currentPosition = Parameter(currentPositionKey, newPos)

      if (currentPosition.head % 10 == 0) {
        val msg   = s"GalilHcd: Moving rod to ${currentPosition.head}mm"
        val event = SystemEvent(componentInfo.prefix, EventName("GalilMovementEvent")).madd(messageKey.set(msg))
        eventService.defaultPublisher.publish(event)
      }

      Thread.sleep(delayMs)
    }

    val stage  = stageKey.set("Setup")
    val status = statusKey.set("Completed")
    val evt    = SystemEvent(componentInfo.prefix, EventName("GalilHcd_status")).madd(stage, status)
    eventService.defaultPublisher.publish(evt)
  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {
    controlCommand match {
      case setup: Setup =>
        val cmdMapEntry = adapter.getCommandMapEntry(setup).get
        val cmdString   = adapter.validateSetup(setup, cmdMapEntry).get
        galilIoActor ! GalilRequest(cmdString, runId, setup.maybeObsId, cmdMapEntry)
      case _ =>
    }
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

  def getGalilConfig: GalilConfig = {
    val conf = ctx.system.settings.config
    val host = if (conf.hasPath("galil.host")) conf.getString("galil.host") else "127.0.0.1"
    val port = if (conf.hasPath("galil.port")) conf.getInt("galil.port") else 8888
    log.info(s"Galil host = $host, port = $port")
    GalilConfig(host, port)
  }

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}
}

// Application main
object GalilHcdApp {
  def main(args: Array[String]): Unit = {
    val defaultConfig = ConfigFactory.load("GalilHcd.conf")
    ContainerCmd.start("galil.hcd.GalilHcd", CSW, args, Some(defaultConfig))
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: Subsystem.