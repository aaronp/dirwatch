package dirscan

import java.nio.file.Path


case class DirEntry(fileName: String, lastModified: Long)

/**
  * The file watch (at least the sun sun.nio.fs.PollingWatchService) is ridiculously slow on Mac OS.
  *
  * So - this is just a way to invoke a shallow check in a directory which can be invoked periodically
  */
object DirEntry {


  def modifiedOrNew(previous: Array[DirEntry], current: Array[DirEntry]): Array[Modified[DirEntry]] = {
    current.filterNot(previous.contains).map(Modified.apply)
  }

  def removed(previous: Array[DirEntry], current: Array[DirEntry]): Array[Removed[DirEntry]] = {
    val currentNames = current.map(_.fileName)
    previous.filterNot { p =>
      val previousFileName = p.fileName
      currentNames.contains(previousFileName)
    }.map(Removed.apply)
  }

  def diff(previous: Array[DirEntry], current: Array[DirEntry]): Array[Diff[DirEntry]] = {
    modifiedOrNew(previous, current) ++ removed(previous, current)
  }

  def entriesInDir(dir: Path): Array[DirEntry] = {
    import eie.io._
    dir.children.map { child =>
      DirEntry(child.fileName, child.lastModifiedMillis)
    }
  }
}
