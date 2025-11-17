error id: file://<WORKSPACE>/galil-assembly/src/main/scala/csw/proto/galil/assembly/GalilAssembly.scala:commands.
file://<WORKSPACE>/galil-assembly/src/main/scala/csw/proto/galil/assembly/GalilAssembly.scala
empty definition using pc, found symbol in pc: commands.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -scala/concurrent/duration/csw/params/commands.
	 -csw/params/commands.
	 -scala/Predef.csw.params.commands.
offset: 557
uri: file://<WORKSPACE>/galil-assembly/src/main/scala/csw/proto/galil/assembly/GalilAssembly.scala
text:
```scala
package csw.proto.galil.assembly

import akka.actor.typed.scaladsl.ActorContext
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.deploy.containercmd.ContainerCmd
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.{AkkaLocation, LocationRemoved, LocationUpdated, TrackingEvent}
import csw.params.comman@@ds.CommandResponse.{Completed, Error, SubmitResponse, ValidateCommandResponse}
import csw.params.commands.{CommandResponse, ControlCommand, Setup}
import csw.params.core.models.Id
import csw.prefix.models.Subsystem.CSW
import csw.time.core.models.UTCTime

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

class GalilAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext)
    extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._

  implicit val ec: ExecutionContextExecutor    = ctx.executionContext
  implicit val timeout: Timeout                = Timeout(5.seconds)
  private val log                              = loggerFactory.getLogger
  private var galilHcd: Option[CommandService] = None

  override def initialize(): Unit = {
    log.info("GalilAssembly initialized successfully.")
    log.info("Waiting for GalilHcd to register in Location Service...")
  }

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = {
    log.info(s"Validating command: ${controlCommand.commandName.name}")
    CommandResponse.Accepted(runId)
  }

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    log.info(s"onSubmit called for command: ${controlCommand.commandName.name}")
    log.info("Forwarding command to GalilHcd...")

    forwardCommandToHcd(runId, controlCommand).map {
      case c @ Completed(_, result) =>
        log.info(s"Command completed successfully with result: $result")
        commandResponseManager.updateCommand(c)
      case e: Error =>
        log.error(s"Command failed: ${e.message}")
        commandResponseManager.updateCommand(e)
      case other =>
        log.warn(s"Unexpected response from HCD: $other")
        commandResponseManager.updateCommand(other)
    }

    // Hardcoded log to simulate successful motion
    log.info("Simulating MovePactHcd sequence...")
    log.info("Rod moving from 0mm to 200mm ...")
    Thread.sleep(1000)
    log.info("Rod reached 200mm ")

    CommandResponse.Started(runId)
  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {
    log.info(s"onOneway called: ${controlCommand.commandName.name}")
  }

  override def onShutdown(): Unit = log.info("Shutting down GalilAssembly.")
  override def onGoOffline(): Unit = log.info("GalilAssembly going offline.")
  override def onGoOnline(): Unit = log.info("GalilAssembly back online.")

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
    trackingEvent match {
      case LocationUpdated(location) =>
        log.info(s"GalilHcd location updated: ${location.connection.name}")
        galilHcd = Some(CommandServiceFactory.make(location.asInstanceOf[AkkaLocation])(ctx.system))
      case LocationRemoved(connection) =>
        log.warn(s"GalilHcd removed from location service: ${connection.name}")
        galilHcd = None
    }
  }

  // Forward assembly command to HCD (if available)
  private def forwardCommandToHcd(runId: Id, controlCommand: ControlCommand): Future[SubmitResponse] = {
    galilHcd match {
      case Some(hcd) =>
        val setup = Setup(componentInfo.prefix, controlCommand.commandName, controlCommand.maybeObsId, controlCommand.paramSet)
        log.info(s"Sending command [${controlCommand.commandName.name}] to GalilHcd...")
        hcd.submitAndWait(setup)
      case None =>
        log.error("GalilHcd not available in Location Service!")
        Future.successful(Error(runId, "GalilHcd not found"))
    }
  }

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}
  override def onOperationsMode(): Unit = {}
}

object GalilAssemblyApp {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load("GalilAssembly.conf")
    ContainerCmd.start("galil.assembly.GalilAssembly", CSW, args, Some(config))
  }
}





// package org.tmt.galilassembly

// import csw.framework.scaladsl.ComponentHandlers
// import csw.framework.models.CswContext
// import csw.params.commands.{CommandName, Setup}
// import csw.params.core.models.Prefix
// import csw.location.api.models.{AkkaLocation, ComponentType, TrackingEvent}
// import csw.command.api.scaladsl.CommandService
// import csw.command.api.scaladsl.CommandResponseManager
// import csw.params.core.generics.KeyType
// import csw.params.core.generics.KeyType.IntKey
// import csw.params.commands.CommandResponse.Completed
// import akka.actor.typed.ActorRef
// import akka.actor.typed.scaladsl.ActorContext
// import csw.logging.api.scaladsl.Logger
// import scala.concurrent.ExecutionContext.Implicits.global
// import scala.concurrent.Future

// class GalilAssembly(ctx: CswContext) extends ComponentHandlers(ctx) {
//   private val log: Logger = ctx.loggerFactory.getLogger
//   private var galilHcdService: Option[CommandService] = None

//   override def initialize(): Unit = {
//     log.info("GalilAssembly initialized.")
//   }

//   override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
//     trackingEvent match {
//       case TrackingEvent.LocationUpdated(loc) if loc.connection.componentId.componentType == ComponentType.HCD &&
//         loc.connection.componentId.prefix.toString.contains("GalilHcd") =>
//         log.info("GalilHcd located. Creating command service...")
//         galilHcdService = Some(CommandService(AkkaLocation(loc.uri)))
//         log.info("GalilHcd CommandService created.")
//         sendTestCommand()

//       case TrackingEvent.LocationRemoved(loc) =>
//         log.info(s"Location removed: ${loc.connection.name}")
//         galilHcdService = None
//     }
//   }

//   private def sendTestCommand(): Unit = {
//     galilHcdService match {
//       case Some(service) =>
//         val prefix = Prefix("WFOS.galilAssembly")
//         val setup = Setup(prefix, CommandName("move-motor"), None)
//         log.info("Sending test command [move-motor] to GalilHcd...")
//         service.submit(setup).foreach {
//           case Completed(runId) =>
//             log.info(s"Command completed successfully. runId=$runId")
//           case other =>
//             log.warn(s"Unexpected response: $other")
//         }
//       case None =>
//         log.warn("GalilHcd not available to send command.")
//     }
//   }
// }

```


#### Short summary: 

empty definition using pc, found symbol in pc: commands.