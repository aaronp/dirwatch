package dirwatch

import java.nio.file.Path
import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import java.util

import com.typesafe.scalalogging.StrictLogging

import scala.collection.mutable.ListBuffer
import scala.compat.Platform.EOL
import scala.sys.process.ProcessLogger

object Execute {

  class BufferLogger(prefix: String, buffSize: Int, errBuffSize: Int) extends ProcessLogger with StrictLogging {
    require(buffSize >= 0)
    require(errBuffSize >= 0)
    private val outBuffer = ListBuffer[String]()
    private val errBuffer = ListBuffer[String]()
    private var outCount = buffSize
    private var errCount = errBuffSize

    def stdOut() = outBuffer.mkString(EOL)

    def stdErr() = errBuffer.mkString(EOL)

    override def out(s: => String): Unit = {
      if (outCount > 0) {
        outCount = outCount - 1
        outBuffer += s
      }
      logger.info(s"$prefix$s")
    }

    override def err(s: => String): Unit = {
      if (errCount > 0) {
        errCount = errCount - 1
        errBuffer += s
      }
      logger.error(s"$prefix$s")
    }

    override def buffer[T](f: => T): T = f
  }

  object BufferLogger {
    def apply(workDir: Path, script: String, buffSize: Int = 1000, errBuffSize: Int = 1000) = {
      new BufferLogger(s"$workDir/$script >", buffSize, errBuffSize)
    }
  }

  def setPerms(script: Path, chmodPerms : String) = {
    import scala.collection.JavaConverters._
    import eie.io._
    script.setFilePermissions(PosixFilePermissions.fromString(chmodPerms).asScala.toSet)
    script
  }
  def runScriptInDir(workDir: Path, script: String, logger: ProcessLogger): Int = {
    import scala.sys.process._
    val sanitizedScript = if (script.startsWith("./")) {
      script
    } else {
      s"./$script"
    }

    val proc = Process(sanitizedScript, Option(workDir.toFile)).run(logger)
    proc.exitValue()
  }

}
