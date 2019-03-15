package dirscan

import java.nio.file.{Path, StandardCopyOption}

import com.typesafe.config.{Config, ConfigFactory}
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
                          defaultRunScript: String,
                          pollFrequency: FiniteDuration,
                          uploadFilePattern: String,
                          readyFilePattern: String
                        ) {

  val UniqueIdFileR: Regex = uploadFilePattern.r
  val ReadyFileR: Regex = readyFilePattern.r


  /** @param config
    * @param file a newly created file has been uploaded
    */
  def onFile(file: Path): Unit = {
    file.fileName match {
      case ReadyFileR(uploadId) =>
        file.parent.foreach { dir =>
          val runScript = Option(file.text).map(_.trim).getOrElse(defaultRunScript)
          processUpload(dir, uploadId, runScript)
        }
      case _ =>
    }
  }

  def processUpload(dir: Path, uploadId: String, runScript: String) = {
    val children = filesWithTheSameId(dir, uploadId, UniqueIdFileR)

    val workDir = uploadDir.resolve(uploadId).mkDirs()
    uploadFilesToWorkDir(children, workDir, UniqueIdFileR, ReadyFileR)

    Execute.runScriptInDir(workDir, runScript, BufferLogger(workDir, runScript, 0, 0))
  }

  def start(implicit scheduler: Scheduler): CancelableFuture[Unit] = {
    val newFiles = DiffStream.diffs(watchDir, pollFrequency).collect {
      case Modified(file) => file
    }
    newFiles.foreach { file =>
      onFile(file)
    }
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

  def fromRootConfig(config: Config): UploadService = {
    apply(config.getConfig("dirwatch"))
  }

  def apply(config: Config = ConfigFactory.load()): UploadService = {
    apply(
      config.getString("watchDir").asPath,
      config.getString("uploadDir").asPath,
      config.getBoolean("createUploadDirIfNotPresent"),
      config.getString("defaultRunScript"),
      config.getDuration("pollFrequency").toMillis.millis,
      config.getString("uploadFilePattern"),
      config.getString("readyFilePattern")
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
