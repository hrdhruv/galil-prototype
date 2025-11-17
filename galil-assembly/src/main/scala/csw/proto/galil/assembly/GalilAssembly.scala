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
import csw.location.client.scaladsl.HttpLocationServiceFactory
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
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

/**
 * GalilAssemblyHandlers is responsible for handling all lifecycle and command interactions
 * between the Galil Assembly and its corresponding HCD(s).
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

  // Called once when the Assembly is initialized
  override def initialize(): Unit = {
    log.info("GalilAssembly initialized successfully.")
    log.info("Waiting for GalilHcd to register in Location Service...")
  }

  // Called whenever location service detects registration/removal of components
  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
    log.info(s"Galil Assembly: onLocationTrackingEvent called with event: $trackingEvent")

    trackingEvent match {
      case LocationUpdated(location) =>
        location.connection match {
          case AkkaConnection(ComponentId(Prefix(Subsystem.CSW, "galil.hcd.GalilHcd"), _)) =>
            log.info("Galil Assembly: Creating command service to GalilHcd")
            galilHcd = Some(CommandServiceFactory.make(location))
            log.info("Galil Assembly: GalilHcd command service initialized successfully.")
          case _ =>
            log.warn(s"Galil Assembly: Unknown HCD encountered for location = $location")
        }

      case LocationRemoved(connection) =>
        connection match {
          case AkkaConnection(ComponentId(Prefix(Subsystem.CSW, "galil.hcd.GalilHcd"), _)) =>
            log.info("Galil Assembly: GalilHcd location removed. Clearing command service reference.")
            galilHcd = None
          case _ =>
            log.warn(s"Galil Assembly: Unknown location removed: $connection")
        }
    }

    if (galilHcd.isDefined) {
      log.info("Galil Assembly: All required HCDs are successfully initialized.")
    }
  }

  // =======================================
  // 🧩 1. New sendCommand() Function (like WFOS)
  // =======================================
  private def sendCommand(runId: Id): SubmitResponse = {
    log.info(s"GalilAssembly: Preparing to send setup command to GalilHcd...")

    // Example parameters (can be replaced by actual Galil control parameters)
    val targetPos: Parameter[Double] = csw.params.core.generics.KeyType.DoubleKey.make("targetPosition").set(400.0)
    val motionType: Parameter[String] = csw.params.core.generics.KeyType.StringKey.make("motionType").set("linear")
    val speed: Parameter[Int] = csw.params.core.generics.KeyType.IntKey.make("speed").set(120)

    val prefix = Prefix(Subsystem.CSW, "galil.assembly.GalilAssembly")
    val command: Setup = Setup(prefix, CommandName("move"), None).madd(targetPos, motionType, speed)

    // Validate locally before submission
    validateCommand(runId, command) match {
      case Accepted(_) =>
        log.info(s"GalilAssembly: Command validated — sending to HCD now.")
        onSubmit(runId, command)
      case Invalid(_, issue) =>
        log.error(s"GalilAssembly: Validation failed — ${issue.reason}")
        Invalid(runId, UnsupportedCommandIssue(issue.reason))
    }
  }

  // =======================================
  // 🧩 2. Send Move Command to HCD
  // =======================================
  private def moveGalilHcd(runId: Id, setup: Setup): Unit = {
    log.info("GalilAssembly: Preparing to move Galil HCD...")

    val targetPositionParam = setup.paramSet.find(_.keyName == "targetPosition").getOrElse {
      log.warn("GalilAssembly: No targetPosition parameter found in setup; using default 500.0")
      csw.params.core.generics.KeyType.DoubleKey.make("targetPosition").set(500.0)
    }

    val prefix = Prefix(Subsystem.CSW, "galil.assembly.GalilAssembly")
    val command = Setup(prefix, CommandName("move"), None).madd(targetPositionParam)

    val connection = AkkaConnection(ComponentId(Prefix("CSW.galil.hcd.GalilHcd"), ComponentType.HCD))
    val maybeLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds)

    maybeLocation match {
      case Some(akkaLocation) =>
        val hcdService = CommandServiceFactory.make(akkaLocation)
        galilHcd = Some(hcdService)
        log.info("GalilAssembly: Sending 'move' command to GalilHcd...")

        val responseF: Future[SubmitResponse] = hcdService.submit(command)
        responseF.foreach {
          case completed: Completed =>
            log.info(s"GalilAssembly: GalilHcd moved successfully; runId=$runId")
            commandResponseManager.updateCommand(completed.withRunId(runId))
          case invalid: Invalid =>
            log.error(s"GalilAssembly: Movement failed; issue=${invalid.issue}")
            commandResponseManager.updateCommand(invalid.withRunId(runId))
          case other =>
            log.warn(s"GalilAssembly: Unexpected response: $other")
            commandResponseManager.updateCommand(other.withRunId(runId))
        }

      case None =>
        log.error("GalilAssembly: Failed to resolve GalilHcd location — not found in Location Service.")
        commandResponseManager.updateCommand(Invalid(runId, RequiredHCDUnavailableIssue("GalilHcd not available")))
    }
  }

  // =======================================
  // 3. Command Validation + Submission Flow
  // =======================================
  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = {
    log.info(s"Validating command: ${controlCommand.commandName.name}")
    Accepted(runId)
  }

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    log.info(s"Galil Assembly: Handling command: $runId")

    // --- Alarm Service Initialization ---
    val locationService = HttpLocationServiceFactory.makeLocalClient
    val alarmAdminAPI: AlarmAdminService = new AlarmServiceFactory().makeAdminApi(locationService)
    val resource = "galil-assembly/src/main/resources/valid-alarms.conf"

    val alarmsConfig: Config = ConfigFactory.parseResources(resource)
    val initFuture: Future[Done] = alarmAdminAPI.initAlarms(alarmsConfig)
    initFuture.foreach(_ => log.info("Galil Assembly: Alarm Service initialized with valid-alarms.conf"))

    // --- Subscribe to HCD telemetry events ---
    val motorEventKey = EventKey(Prefix("CSW.galil.hcd.GalilHcd"), EventName("MotorPositionEvent"))

    subscriber.subscribeCallback(Set(motorEventKey), event => {
      log.info(s"Galil Assembly: Received MotorPositionEvent -> $event")
    })

    controlCommand match {
      case setup: Setup => onSetup(runId, setup)
      case _: Observe   => Invalid(runId, WrongCommandTypeIssue("GalilAssembly cannot handle Observe commands"))
    }
  }

  // =======================================
  // 4. Handle Setup Command
  // =======================================
  private def onSetup(runId: Id, setup: Setup): SubmitResponse = {
    setup.commandName match {
      case CommandName("move-motor") =>
        log.info("Galil Assembly: move-motor command received from sequencer")
        moveGalilHcd(runId, setup)
      case CommandName("send-command") =>
        log.info("Galil Assembly: send-command received — invoking sendCommand()")
        sendCommand(runId)
      case other =>
        log.warn(s"Galil Assembly: Unknown setup command received: ${other.name}")
    }
    
    Started(runId)
  }

  // =======================================
  // 5. Lifecycle + Oneway
  // =======================================
  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}
  override def onShutdown(): Unit = log.info("Shutting down GalilAssembly.")
  override def onGoOffline(): Unit = log.info("GalilAssembly going offline.")
  override def onGoOnline(): Unit = log.info("GalilAssembly back online.")
  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}
  override def onOperationsMode(): Unit = {}
}

/**
 * Application entry point for running GalilAssembly container locally.
 */
object GalilAssemblyApp {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load("GalilAssembly.conf")
    ContainerCmd.start("csw.galil.assembly.GalilAssembly", CSW, args, Some(config))
  }
}
