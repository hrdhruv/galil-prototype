error id: file://<WORKSPACE>/galil-hcd/src/main/scala/csw/proto/galil/hcd/GalilHcd.scala:typed.
file://<WORKSPACE>/galil-hcd/src/main/scala/csw/proto/galil/hcd/GalilHcd.scala
empty definition using pc, found symbol in pc: typed.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -csw/params/commands/CommandResponse.org.apache.pekko.actor.typed.
	 -org/apache/pekko/actor/typed.
	 -scala/Predef.org.apache.pekko.actor.typed.
offset: 86
uri: file://<WORKSPACE>/galil-hcd/src/main/scala/csw/proto/galil/hcd/GalilHcd.scala
text:
```scala
package csw.proto.galil.hcd
package csw.proto.galil.hcd
import org.apache.pekko.actor.@@typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.deploy.containercmd.ContainerCmd
import com.typesafe.config.ConfigFactory
import csw.prefix.models.Subsystem
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse._
import csw.params.core.models.Id
import csw.params.commands.CommandIssue.UnsupportedCommandIssue
import csw.params.commands.{ControlCommand, Setup}
import csw.time.core.models.UTCTime
import csw.params.core.generics.Parameter
import csw.params.events.{SystemEvent, EventName}

import scala.concurrent.ExecutionContextExecutor

class GalilHcdHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext)
    extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger
  private val prefix                        = cswCtx.componentInfo.prefix
  private val publisher                     = eventService.defaultPublisher

  override def initialize(): Unit = {
    log.info(s"Initializing $prefix")
    log.info(s"GalilHcd : Checking if $prefix is at its current position")

    log.info(s"Current Position - ${GalilInfo.currentPosition.head}")
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = {
    Accepted(runId)
  }

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    log.info(s"GalilHcd : Handling command with runId - $runId")
    controlCommand match {
      case setup: Setup => onSetup(runId, setup)
      case _            => Invalid(runId, UnsupportedCommandIssue("GalilHcd : Invalid Command received"))
    }
  }

  private def onSetup(runId: Id, setup: Setup): SubmitResponse = {
    println(s"GalilHcd : Received command - OnSubmit123")
    log.info(s"GalilHcd : Executing the received command with runId - $runId")

    val delay: Int                        = 1000
    val targetPosition: Parameter[Double] = setup(GalilInfo.targetPositionKey)

    log.info(s"GalilHcd : Rod is currently at ${GalilInfo.currentPosition.head}mm")

    while (GalilInfo.currentPosition.head != targetPosition.head) {
      GalilInfo.currentPosition = GalilInfo.currentPositionKey.set(
        if (GalilInfo.currentPosition.head < targetPosition.head)
          GalilInfo.currentPosition.head + 50 // Move forward
        else
          GalilInfo.currentPosition.head - 50 // Move backward
      )

      if (GalilInfo.currentPosition.head % 10 == 0) {
        val message = s"GalilHcd : Moving rod to ${GalilInfo.currentPosition.head}mm"
        // Create and publish the event
        val event = createMovementEvent(message)
        publisher.publish(event)
      }
      Thread.sleep(delay)
    }

    val stage  = GalilInfo.stageKey.set("Setup")
    val status = GalilInfo.statusKey.set("Completed")
    val event  = SystemEvent(componentInfo.prefix, EventName("GalilHcd_status")).madd(stage, status)
    publisher.publish(event)
    Completed(runId)
  }

  private def createMovementEvent(message: String): SystemEvent = {
    // Create a SystemEvent representing the movement of the actuator
    SystemEvent(componentInfo.prefix, EventName("GalilMovementEvent"))
      .madd(GalilInfo.messageKey.set(message))
  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}
object GalilHcdApp {
  def main(args: Array[String]): Unit = {
    // Start the HCD container with Subsystem.CSW
    ContainerCmd.start("galil.hcd.GalilHcd", Subsystem.CSW, args)
  }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: typed.