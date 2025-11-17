error id: file://<WORKSPACE>/galil-hcd/src/main/scala/csw/proto/galil/hcd/GalilIOActor.scala:pekko.
file://<WORKSPACE>/galil-hcd/src/main/scala/csw/proto/galil/hcd/GalilIOActor.scala
empty definition using pc, found symbol in pc: pekko.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -org/apache/pekko.
	 -scala/Predef.org.apache.pekko.
offset: 78
uri: file://<WORKSPACE>/galil-hcd/src/main/scala/csw/proto/galil/hcd/GalilIOActor.scala
text:
```scala
package csw.proto.galil.hcd

import java.io.IOException

import org.apache.pek@@ko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.util.ByteString
import csw.command.client.CommandResponseManager
import csw.framework.CurrentStatePublisher
import csw.logging.client.scaladsl.LoggerFactory
import csw.params.commands.CommandResponse.Completed
import csw.params.commands.Result
import csw.params.core.models.{Id, ObsId}
import csw.params.core.states.{CurrentState, StateName}
import csw.prefix.models.Prefix
import csw.proto.galil.hcd.CSWDeviceAdapter.CommandMapEntry
import csw.proto.galil.io.{DataRecord, GalilIo, GalilIoTcp}

/**
 * Worker actor that handles the Galil I/O
 */
private[hcd] object GalilIOActor {

  // Messages handled by this actor
  sealed trait GalilCommandMessage
  case class GalilRequest(
      commandString: String,
      runId: Id,
      maybeObsId: Option[ObsId],
      commandKey: CommandMapEntry
  ) extends GalilCommandMessage

  case class GalilCommand(commandString: String) extends GalilCommandMessage

  val publishDataRecord = "publishDataRecord"

  def behavior(
      galilConfig: GalilConfig,
      commandResponseManager: CommandResponseManager,
      adapter: CSWDeviceAdapter,
      loggerFactory: LoggerFactory,
      galilPrefix: Prefix,
      currentStatePublisher: CurrentStatePublisher
  ): Behavior[GalilCommandMessage] =
    Behaviors.setup { ctx =>
      val log = loggerFactory.getLogger

      // Connect to Galil device
      val galilIo: GalilIo = try {
        GalilIoTcp(galilConfig.host, galilConfig.port)
      } catch {
        case ex: Exception =>
          log.error(s"Failed to connect to Galil at ${galilConfig.host}:${galilConfig.port}")
          throw ex
      }

      def galilSend(cmd: String): String = {
        log.info(s"Sending '$cmd' to Galil")
        val responses = galilIo.send(cmd)
        if (responses.lengthCompare(1) != 0)
          throw new RuntimeException(s"Received ${responses.size} responses to Galil $cmd")
        val resp = responses.head._2.utf8String
        log.debug(s"Response from Galil: $resp")
        resp
      }

      def publishDataRecord(): Unit = {
        val response = galilIo.send("QR")
        val bs       = response.head._2
        val dr       = DataRecord(bs)
        val cs       = CurrentState(galilPrefix, StateName("DataRecord"), dr.toParamSet)
        currentStatePublisher.publish(cs)
      }

      def handleDataRecordResponse(dr: DataRecord, runId: Id, maybeObsId: Option[ObsId], cmdMapEntry: CommandMapEntry): Unit = {
        val returnResponse = DataRecord.makeCommandResponse(runId, maybeObsId, dr)
        commandResponseManager.updateCommand(returnResponse)
      }

      def handleDataRecordRawResponse(bs: ByteString, runId: Id, maybeObsId: Option[ObsId], cmdMapEntry: CommandMapEntry): Unit = {
        val returnResponse = Completed(runId, new Result().add(DataRecord.key.set(bs.toByteBuffer.array())))
        commandResponseManager.updateCommand(returnResponse)
      }

      def handleGalilResponse(response: String, runId: Id, maybeObsId: Option[ObsId], cmdMapEntry: CommandMapEntry): Unit = {
        val returnResponse = adapter.makeResponse(runId, maybeObsId, cmdMapEntry, response)
        commandResponseManager.updateCommand(returnResponse)
      }

      Behaviors.receiveMessage[GalilCommandMessage] {
        case GalilCommand(commandString) =>
          if (commandString == GalilIOActor.publishDataRecord) {
            publishDataRecord()
            ctx.scheduleOnce(1.second, ctx.self, GalilCommand(GalilIOActor.publishDataRecord))
          }
          Behaviors.same

        case GalilRequest(commandString, runId, maybeObsId, commandKey) =>
          if (commandString.startsWith("QR")) {
            val response = galilIo.send(commandString)
            val bs       = response.head._2
            if (commandKey.name == "getDataRecord") {
              val dr = DataRecord(bs)
              handleDataRecordResponse(dr, runId, maybeObsId, commandKey)
            } else {
              handleDataRecordRawResponse(bs, runId, maybeObsId, commandKey)
            }
          } else {
            val response = galilSend(commandString)
            handleGalilResponse(response, runId, maybeObsId, commandKey)
          }
          Behaviors.same
      }
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: pekko.