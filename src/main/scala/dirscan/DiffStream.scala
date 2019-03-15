package dirscan

import java.nio.file.Path

import com.typesafe.scalalogging.StrictLogging
import monix.execution.Scheduler
import monix.reactive.{Observable, OverflowStrategy}
import cats.kernel.Eq
import cats.syntax.functor._

import scala.concurrent.duration._

/**
  * Provides a stream of directory differences ([[Diff]] entries) when files get created/modified or removed
  */
object DiffStream extends StrictLogging {

  type DiffEntry = Diff[DirEntry]

  object implicits {
    implicit lazy val ioScheduler = Scheduler.io(DiffStream.getClass.getSimpleName)
  }

  /** @param dir           the directory to check
    * @param pollFrequency how often to check for changes
    * @param sched         the scheduler
    * @return a stream of differences for the directory which is checked every 'pollFrequency'
    */
  def diffs(dir: Path, pollFrequency: FiniteDuration)(implicit sched: Scheduler): Observable[Diff[Path]] = {
    changeStream(dir, pollFrequency).map(_._2).flatMap { diffs =>
      Observable.fromIterable(diffs).map { entry =>
        entry.map(_.fileName).map(dir.resolve)
      }
    }
  }

  /** @param dir           the directory to check
    * @param pollFrequency how often we should try and check for changes
    * @param sched         the scheduler to check for changes
    * @return a stream of directory contents (filename + last modified) and (potentially empty) differences
    */
  def changeStream(dir: Path, pollFrequency: FiniteDuration)(implicit sched: Scheduler): Observable[(Array[DirEntry], Array[DiffEntry])] = {
    def ls(): Array[DirEntry] = DirEntry.entriesInDir(dir)

    val initialContents = ls
    logger.info(s"Starting stream in $dir w/ ${initialContents.length} initial entries")
    Observable.interval(pollFrequency).asyncBoundary(OverflowStrategy.DropOld(2)).scan(initialContents -> Array.empty[DiffEntry]) {
      case ((previousContents, _), _) =>
        val newContents = ls()
        val diffs: Array[DiffEntry] = DirEntry.diff(previousContents, newContents)
        logger.debug(s"$dir has ${newContents.length} entries, diff ${diffs.length}")
        newContents -> diffs
    }.distinctUntilChanged(Eq.fromUniversalEquals)
  }
}
