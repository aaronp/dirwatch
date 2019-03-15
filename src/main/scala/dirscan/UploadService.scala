package dirscan

import java.nio.file.{Path, StandardCopyOption}
import eie.io._
import dirscan.Execute.BufferLogger

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

  private val UniqueIdFileR = "(.*)__(.*)".r
  private val ReadyFileR = "(.*).ready".r

  def update(file: Path) = {
    file.fileName match {
      case UniqueIdFileR(id, fileName) =>
      case ReadyFileR(id) =>
    }
  }

  case class Impl(watchDir: Path, readyDir: Path) {
    def update(file: Path) = {
      file.fileName match {
        case UniqueIdFileR(id, fileName) =>


        case ReadyFileR(uploadId) =>
          file.parent.foreach { dir =>
            processUpload(dir, readyDir, uploadId)
          }
      }
    }
  }

  def processUpload(dir: Path, readyDir: Path, uploadId: String) = {
    val children = filesWithTheSameId(dir, uploadId)

    val workDir = readyDir.resolve(uploadId).mkDirs()
    uploadFilesToWorkDir(children, workDir)

    val script = "run.sh"
    Execute.invokeRunScript(workDir, script, BufferLogger(workDir, script, 0, 0))
  }

  def uploadFilesToWorkDir(files: Iterator[Path], toDir: Path) = {
    files.foreach { file =>
      file.fileName match {
        case UniqueIdFileR(_, fileName) =>
          val toFile = toDir.resolve(fileName)
          file.moveTo(toFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.COPY_ATTRIBUTES)
        case ReadyFileR(_) => file.delete(true)
      }
    }
  }

  def filesWithTheSameId(inDir: Path, uniqueId: String): Iterator[Path] = {
    inDir.childrenIter.filter { child =>
      child.fileName match {
        case UniqueIdFileR(id, _) => id == uniqueId
      }
    }

  }
}
