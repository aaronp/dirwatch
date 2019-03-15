package dirwatch

import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import dirwatch.Execute.BufferLogger
import eie.io._
import monix.execution.{CancelableFuture, Scheduler}

import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.matching.Regex

/** Represents the parsed configuration for the upload service
  *
  * @param uploadDir the watched directory
  * @param execDir   the execution (working) directory
  * @param defaultRunScript
  * @param pollFrequency
  */
case class UploadService(uploadDir: Path,
                         execDir: Path,
                         createUploadDirIfNotPresent: Boolean,
                         uploadDirPerms: String,
                         runScriptPerms: String,
                         defaultRunScript: String,
                         pollFrequency: FiniteDuration,
                         uploadFilePattern: String,
                         readyFilePattern: String
                        ) extends LazyLogging {

  val UniqueIdFileR: Regex = uploadFilePattern.r
  val ReadyFileR: Regex = readyFilePattern.r

  private def uploadPerms = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(uploadDirPerms))

  /** @param config
    * @param file a newly created file has been uploaded
    */
  def onFile(file: Path): Unit = {
    file.fileName match {
      case ReadyFileR(uploadId) =>
        logger.info(s"$uploadId ready")
        val runScript = Option(file.text).map(_.trim).getOrElse(defaultRunScript)
        try {
          val retVal = processUpload(uploadId, runScript)
          logger.info(s"Running ${runScript} for $uploadId returned '$retVal'")
        } catch {
          case NonFatal(e) =>
            logger.error(s"Running ${runScript} for $uploadId threw '${e.getMessage}'", e)
        }
      case _ => logger.debug(s"ignoring ${uploadDir.relativize(file)} which doesn't match '$readyFilePattern'")
    }
  }

  def processUpload(uploadId: String, runScript: String): Int = {
    val (workDir, files) = uploadFilesToWorkDir(uploadId)
    logger.info(s"Moved ${files.length} files to $workDir for $uploadId, invoking $runScript")
    Execute.setPerms(workDir.resolve(runScript), runScriptPerms)
    Execute.runScriptInDir(workDir, runScript, BufferLogger(workDir, runScript, 0, 0))
  }

  def start(implicit scheduler: Scheduler): CancelableFuture[Unit] = {
    if (createUploadDirIfNotPresent && !execDir.isDir) {
      logger.info(s"Creating $execDir w/ $uploadDirPerms")
      execDir.mkDirs(uploadPerms)
    }

    logger.info(s"Watching $uploadDir every $pollFrequency and uploading to $execDir")
    val newFiles = DiffStream.diffs(uploadDir, pollFrequency).collect {
      case Modified(file) => file
    }
    newFiles.foreach(onFile)
  }


  /**
    * Moves the files which match the 'uniqueId' for the patterns into the 'toDir'.
    *
    * The 'ready' file will be deleted
    *
    * @return the files which were moved
    */
  def uploadFilesToWorkDir(uniqueId: String): (Path, Array[Path]) = {
    val toDir = execDir.resolve(uniqueId).mkDirs(uploadPerms)
    val moved = uploadDir.children.flatMap { file =>
      file.fileName match {
        case UniqueIdFileR(`uniqueId`, fileName) =>
          val toFile = toDir.resolve(fileName)
          file.moveTo(toFile)
          Option(toFile)
        case ReadyFileR(`uniqueId`) =>
          file.delete(true)
          None
        case _ => None
      }
    }
    (toDir, moved)
  }
}

/**
  * The idea is that files are uploaded in the form <unique-id>__<whatever>.
  *
  * When we see '<unique-id>.ready', the files matching <unique-id>__* are then moved into the directory <unique-id>
  * sans the <unique-id> part and then the 'run.sh' is invoked if it exists.
  *
  * NOTE: We also create a '.upload' file which contains metadata (currently just creation time) so that we can
  * more easily perform house-keeping stuff.
  */
object UploadService {

  def fromRootConfig(rootConfig: Config = ConfigFactory.load()): UploadService = {
    apply(rootConfig.getConfig("dirwatch"))
  }

  def apply(dirWatchConfig: Config): UploadService = {
    import dirWatchConfig._
    apply(
      getString("uploadDir").asPath,
      getString("execDir").asPath,
      getBoolean("createUploadDirIfNotPresent"),
      getString("uploadDirPerms"),
      getString("runScriptPerms"),
      getString("defaultRunScript"),
      getDuration("pollFrequency").toMillis.millis,
      getString("uploadFilePattern"),
      getString("readyFilePattern")
    )
  }
}
