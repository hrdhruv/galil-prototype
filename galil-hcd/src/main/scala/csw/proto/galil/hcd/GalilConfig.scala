package csw.proto.galil.hcd

/**
 * Galil configuration
 * @param host host or IP address of the Galil device
 * @param port port number on host
 */
case class GalilConfig(host: String, port: Int)

// csw-services start -e

// export INTERFACE_NAME=lo
// export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
// export PATH=$JAVA_HOME/bin:$PATH
// export TMT_LOG_HOME=/home/harsh-d/Desktop/galil-prototype/logs
// mkdir -p /home/harsh-d/Desktop/galil-prototype/logs

// sbt "galil-hcd/runMain csw.proto.galil.hcd.GalilHcdApp --local /home/harsh-d/Desktop/galil-prototype/galil-hcd/src/main/resources/GalilHcd.conf"