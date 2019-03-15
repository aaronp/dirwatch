package dirscan

import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Path, StandardCopyOption}

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import dirscan.DiffStream.logger
import dirscan.Execute.BufferLogger
import dirscan.UploadService.{filesWithTheSameId, uploadFilesToWorkDir}
import eie.io._
import monix.execution.{CancelableFuture, Scheduler}

import scala.concurrent.duration._
import scala.util.matching.Regex

/** Represents the parsed configuration for the upload service
  *
  * @param watchDir
  * @param uploadDir
  * @param defaultRunScript
  * @param pollFrequency
  */
case class UploadService(
                          watchDir: Path,
                          uploadDir: Path,
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


  /** @param config
    * @param file a newly created file has been uploaded
    */
  def onFile(file: Path): Unit = {
    file.fileName match {
      case ReadyFileR(uploadId) =>
        logger.info(s"$uploadId ready")
        file.parent.foreach { dir =>
          val runScript = Option(file.text).map(_.trim).getOrElse(defaultRunScript)
          logger.info(s"Invoking $runScript in $dir for $uploadId")
          processUpload(dir, uploadId, runScript)
        }
      case _ => logger.debug(s"ignoring $file which doesn't match '$readyFilePattern'")
    }
  }

  def processUpload(dir: Path, uploadId: String, runScript: String) = {
    val children = filesWithTheSameId(dir, uploadId, UniqueIdFileR)

    val workDir = uploadDir.resolve(uploadId).mkDirs()
    uploadFilesToWorkDir(children, workDir, UniqueIdFileR, ReadyFileR)

    Execute.setPerms(workDir.resolve(runScript), runScriptPerms)
    Execute.runScriptInDir(workDir, runScript, BufferLogger(workDir, runScript, 0, 0))
  }

  def start(implicit scheduler: Scheduler): CancelableFuture[Unit] = {
    if (createUploadDirIfNotPresent && !uploadDir.isDir) {
      logger.info(s"Creating $uploadDir w/ $uploadDirPerms")
      uploadDir.mkDirs(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(uploadDirPerms)))
    }

    logger.info(s"Watching $watchDir every $pollFrequency and uploading to $uploadDir")
    val newFiles = DiffStream.diffs(watchDir, pollFrequency).collect {
      case Modified(file) => file
    }
    newFiles.foreach(onFile)
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
      getString("watchDir").asPath,
      getString("uploadDir").asPath,
      getBoolean("createUploadDirIfNotPresent"),
      getString("uploadDirPerms"),
      getString("runScriptPerms"),
      getString("defaultRunScript"),
      getDuration("pollFrequency").toMillis.millis,
      getString("uploadFilePattern"),
      getString("readyFilePattern")
    )
  }

  def uploadFilesToWorkDir(files: Iterator[Path], toDir: Path, UniqueIdFileR: Regex, ReadyFileR: Regex) = {
    files.foreach { file =>
      file.fileName match {
        case UniqueIdFileR(_, fileName) =>
          val toFile = toDir.resolve(fileName)
          file.moveTo(toFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.COPY_ATTRIBUTES)
        case ReadyFileR(_) => file.delete(true)
      }
    }
  }

  def filesWithTheSameId(inDir: Path, uniqueId: String, UniqueIdFileR: Regex): Iterator[Path] = {
    inDir.childrenIter.filter { child =>
      child.fileName match {
        case UniqueIdFileR(id, _) => id == uniqueId
      }
    }
  }
}
