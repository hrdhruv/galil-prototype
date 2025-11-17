package csw.proto.galil.hcd

import csw.params.core.generics.{Key, KeyType, Parameter}
import csw.params.core.models.ObsId

object GalilInfo {
  // Physical movement constraints (example: change values if Galil hardware is different)
  val maxExtensionKey: Key[Double]    = KeyType.DoubleKey.make("maxExtension")
  val maxExtension: Parameter[Double] = maxExtensionKey.set(1000.0) // Example: Galil might allow longer extension

  val minExtensionKey: Key[Double]    = KeyType.DoubleKey.make("minExtension")
  val minExtension: Parameter[Double] = minExtensionKey.set(0.0)

  val movementStepKey: Key[Double]    = KeyType.DoubleKey.make("movementStep")
  val movementStep: Parameter[Double] = movementStepKey.set(50.0)

  // Position keys
  val targetPositionKey: Key[Double]    = KeyType.DoubleKey.make("targetPosition")
  val targetPosition: Parameter[Double] = targetPositionKey.set(500.0)

  val currentPositionKey: Key[Double]    = KeyType.DoubleKey.make("currentPosition")
  var currentPosition: Parameter[Double] = currentPositionKey.set(0.0)

  // Retry count
  val retryCountKey: Key[Int]    = KeyType.IntKey.make("retryCount")
  var retryCount: Parameter[Int] = retryCountKey.set(0)

  // Event parameters
  val stageKey: Key[String]   = KeyType.StringKey.make("stage")
  val statusKey: Key[String]  = KeyType.StringKey.make("status")
  val messageKey: Key[String] = KeyType.StringKey.make("message")

  // Observation ID (example one, you can replace with your actual ObsId usage)
  val obsId: ObsId = ObsId("2025A-001-001")
}
