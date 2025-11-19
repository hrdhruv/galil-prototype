package csw.proto.galil.assembly

import akka.actor.typed.scaladsl.ActorContext
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import csw.alarm.api.scaladsl.AlarmAdminService
import csw.alarm.client.AlarmServiceFactory
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.command.client.messages.TopLevelActorMessage
import csw.event.api.scaladsl.EventSubscriber
import csw.event.client.EventServiceFactory
import csw.framework.deploy.containercmd.ContainerCmd
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models._
import csw.location.api.models.Connection.AkkaConnection
import csw.params.commands.CommandResponse._
import csw.params.commands.CommandIssue._
import csw.params.commands.{CommandName, ControlCommand, Observe, Setup}
import csw.params.core.generics.Parameter
import csw.params.core.models.Id
import csw.params.events.{EventKey, EventName}
import csw.prefix.models.{Prefix, Subsystem}
import csw.prefix.models.Subsystem.CSW
import csw.time.core.models.UTCTime
import csw.command.client.CommandResponseManager
import akka.Done

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

/**
 * GalilAssemblyHandlers connects to the Galil HCD and sends movement commands.
 */
class GalilAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext)
    extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._

  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  implicit val timeout: Timeout = Timeout(5.seconds)
  implicit val system: akka.actor.typed.ActorSystem[Nothing] = ctx.system

  private val log = loggerFactory.getLogger
  private var galilHcd: Option[CommandService] = None
  private val eventService = new EventServiceFactory().make(locationService)
  private val subscriber: EventSubscriber = eventService.defaultSubscriber
  private val commandResponseManager: CommandResponseManager = cswCtx.commandResponseManager

  override def initialize(): Unit = {
    log.info("GalilAssembly initialized successfully.")
    log.info("Waiting for GalilHcd to register in Location Service...")
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
    trackingEvent match {
      case LocationUpdated(location) =>
        location.connection match {
          case AkkaConnection(ComponentId(Prefix(CSW, "galil.hcd.GalilHcd"), _)) =>
            log.info("Galil Assembly: GalilHcd located — creating CommandService.")
            galilHcd = Some(CommandServiceFactory.make(location))
            log.info("Galil Assembly: GalilHcd CommandService initialized successfully.")
            log.info("Galil Assembly: All required HCDs are successfully initialized.")

            // Automatically send test command once HCD is ready
            sendTestCommandToHcd()

          case _ =>
            log.warn(s"Galil Assembly: Unknown location registered: $location")
        }

      case LocationRemoved(connection) =>
        connection match {
          case AkkaConnection(ComponentId(Prefix(CSW, "galil.hcd.GalilHcd"), _)) =>
            log.warn("Galil Assembly: GalilHcd location removed.")
            galilHcd = None
          case _ =>
            log.warn(s"Galil Assembly: Unknown location removed: $connection")
        }
    }
  }

  /**
   * Sends a test "move" command to GalilHcd once it is registered.
   */
  private def sendTestCommandToHcd(): Unit = {
    galilHcd match {
      case Some(service) =>
        log.info("GalilAssembly: Sending test move command to GalilHcd...")

        val prefix = Prefix(CSW, "galil.assembly.GalilAssembly")
        val targetPos: Parameter[Double] =
          csw.params.core.generics.KeyType.DoubleKey.make("targetPosition").set(500.0)
        val setup = Setup(prefix, CommandName("move-motor"), None).madd(targetPos)

        // Subscribe to position updates
        val motorEventKey = EventKey(Prefix(CSW, "galil.hcd.GalilHcd"), EventName("MotorPositionEvent"))
        subscriber.subscribeCallback(Set(motorEventKey), event => {
          log.info(s"Received MotorPositionEvent -> $event")
        })

        val responseF: Future[SubmitResponse] = service.submitAndWait(setup)
        responseF.foreach {
          case Completed(id, result) =>
            log.info(s"GalilAssembly: GalilHcd completed command successfully. runId=$id result=$result")
        
          case Invalid(id, issue) =>
            log.error(s"GalilAssembly: Command failed for runId=$id. Reason: ${issue.reason}")
        
          case other =>
            log.warn(s"GalilAssembly: Unexpected response: $other")
        }


      case None =>
        log.error("GalilAssembly: GalilHcd CommandService not yet available.")
    }
  }

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = {
    log.info(s"Validating command: ${controlCommand.commandName.name}")
    Accepted(runId)
  }

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    log.info(s"Galil Assembly: Handling external command ${controlCommand.commandName.name}")
    controlCommand match {
      case setup: Setup => moveGalilHcd(runId, setup)
      case _: Observe   => Invalid(runId, WrongCommandTypeIssue("GalilAssembly cannot handle Observe commands"))
    }
    Started(runId)
  }

  private def moveGalilHcd(runId: Id, setup: Setup): Unit = {
    galilHcd match {
      case Some(service) =>
        log.info("GalilAssembly: Forwarding Setup command to GalilHcd...")
        val responseF = service.submit(setup)
        responseF.foreach {
          case Completed(id, result) =>
            log.info(s"Movement completed for runId=$id result=$result")
        
          case Invalid(id, issue) =>
            log.error(s"Command failed for runId=$id. Reason: ${issue.reason}")
        
          case other =>
            log.warn(s"Unexpected response: $other")
        }

      case None =>
        log.error("GalilAssembly: GalilHcd not available for command execution.")
    }
  }

  // ---- Lifecycle Methods ----
  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}
  override def onShutdown(): Unit = log.info("Shutting down GalilAssembly.")
  override def onGoOffline(): Unit = log.info("GalilAssembly going offline.")
  override def onGoOnline(): Unit = log.info("GalilAssembly back online.")
  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}
  override def onOperationsMode(): Unit = {}
}

object GalilAssemblyApp {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load("GalilAssembly.conf")
    ContainerCmd.start("csw.galil.assembly.GalilAssembly", CSW, args, Some(config))
  }
}
