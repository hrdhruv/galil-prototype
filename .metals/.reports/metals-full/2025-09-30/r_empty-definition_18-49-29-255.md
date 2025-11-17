error id: file://<WORKSPACE>/galil-hcd/src/main/scala/csw/proto/galil/hcd/GalilInfo.scala:`<none>`.
file://<WORKSPACE>/galil-hcd/src/main/scala/csw/proto/galil/hcd/GalilInfo.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -csw/params/core/generics/Parameter#
	 -Parameter#
	 -scala/Predef.Parameter#
offset: 681
uri: file://<WORKSPACE>/galil-hcd/src/main/scala/csw/proto/galil/hcd/GalilInfo.scala
text:
```scala
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
  val movementStep: Paramete@@r[Double] = movementStepKey.set(50.0)

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

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.