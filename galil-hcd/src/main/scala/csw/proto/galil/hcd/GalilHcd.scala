package csw.proto.galil.hcd

import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.deploy.containercmd.ContainerCmd
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse._
import csw.params.commands.CommandIssue.UnsupportedCommandIssue
import csw.params.commands.{ControlCommand, Setup}
import csw.params.core.models.Id
import csw.params.events.{EventName, SystemEvent}
import csw.prefix.models.Subsystem
import csw.time.core.models.UTCTime

import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps

/**
 * GalilHcdHandlers simulates the Galil Hardware Control Device.
 * It receives setup commands (e.g., move-motor) and simulates actuator movement.
 * It publishes position updates as SystemEvents for the Assembly to subscribe to.
 */
class GalilHcdHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext)
    extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._

  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger
  private val prefix                        = cswCtx.componentInfo.prefix
  private val publisher                     = eventService.defaultPublisher

  override def initialize(): Unit = {
    log.info(s"Initializing $prefix")
    log.info(s"GalilHcd: Checking current actuator position")
    log.info(s"Current Position - ${GalilInfo.currentPosition.head} mm")
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = {
    log.info(s"Validating command: ${controlCommand.commandName.name}")
    Accepted(runId)
  }

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    log.info(s"GalilHcd: Handling command with runId - $runId")
    controlCommand match {
      case setup: Setup => onSetup(runId, setup)
      case _            => Invalid(runId, UnsupportedCommandIssue("GalilHcd: Unsupported Command"))
    }
  }

  /**
   * Simulates actuator movement with an in–out–in cycle,
   * publishing intermediate position updates.
   */
  private def onSetup(runId: Id, setup: Setup): SubmitResponse = {
    println(s"GalilHcd : Received command - OnSubmit - Executing setup")
    log.info(s"GalilHcd : Executing the received command with runId - $runId")

    val step  = GalilInfo.movementStep.head
    val min   = GalilInfo.minExtension.head
    val max   = GalilInfo.maxExtension.head
    val delay = 50 // milliseconds per step

    def moveActuator(target: Double): Unit = {
      var pos = GalilInfo.currentPosition.head
      while (Math.abs(pos - target) > step) {
        pos =
          if (pos < target) Math.min(pos + step, max)
          else Math.max(pos - step, min)

        GalilInfo.currentPosition = GalilInfo.currentPositionKey.set(pos)
        val message = s"GalilHcd : Moving actuator to $pos mm"

        val event = createMovementEvent(message, pos)
        publisher.publish(event)

        Thread.sleep(delay)
      }

      // Snap to final target
      GalilInfo.currentPosition = GalilInfo.currentPositionKey.set(target)
      log.info(s"GalilHcd : Actuator reached target position ${GalilInfo.currentPosition.head} mm")

      // val event = createMovementEvent(s"Reached target $target mm", target)
      // publisher.publish(event)
    }

    val current = GalilInfo.currentPosition.head
    log.info(s"GalilHcd : Actuator is currently at $current mm")

    // 1️⃣ Move forward to in-position (500 mm)
    moveActuator(500)

    // 2️⃣ Wait briefly
    log.info("GalilHcd : Holding position for 2 seconds before returning")
    Thread.sleep(2000)

    // 3️⃣ Move back to out-position (0 mm)
    moveActuator(0)

    // Final status event
    // val statusEvent = SystemEvent(componentInfo.prefix, EventName("GalilHcdStatus"))
    //   .madd(GalilInfo.statusKey.set("Cycle Completed"))
    // publisher.publish(statusEvent)

    // log.info("GalilHcd : Actuator cycle completed successfully.")
    Completed(runId)
  }

  /**
   * Publishes position and message as movement event updates.
   */
  private def createMovementEvent(message: String, position: Double): SystemEvent = {
    SystemEvent(componentInfo.prefix, EventName("MotorPositionEvent"))
      .madd(
        GalilInfo.messageKey.set(message),
        GalilInfo.currentPositionKey.set(position)
      )
  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = log.info("Shutting down GalilHcd.")
  override def onGoOffline(): Unit = log.info("GalilHcd going offline.")
  override def onGoOnline(): Unit = log.info("GalilHcd back online.")
  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}
  override def onOperationsMode(): Unit = {}
}

/**
 * Application entry point for Galil HCD.
 */
object GalilHcdApp {
  def main(args: Array[String]): Unit = {
    ContainerCmd.start("csw.galil.hcd.GalilHcd", Subsystem.CSW, args)
  }
}