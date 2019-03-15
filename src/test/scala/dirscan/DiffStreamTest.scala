package dirscan

import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path}

import monix.reactive.Observable

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

class DiffStreamTest extends BaseSpec {

  "DiffStream" should {
    "stream diffs" in withTestDir { dir =>

      Given("A stream of directory changes")
      import DiffStream.implicits._
      val stream: Observable[Diff[Path]] = DiffStream.diffs(dir, 100.millis).share

      // ensure we consume each element for the 'take(1)' to work below...
      val changes = ListBuffer[Diff[Path]]()
      stream.foreach { diff: Diff[Path] =>
        changes += diff
      }

      def nextDiff(): Diff[Path] = {
        eventually {
          val List(value) = changes.toList
          changes.clear()
          value
        }
      }

      When("A new file is created in the directory")
      val hello = dir.resolve("hello.txt").text = "Hello"

      Then("a Modified event should be published")
      val Modified(helloCreated) = nextDiff()
      helloCreated shouldBe hello

      When("the file's last modified time is changed (by at least 1 second)")
      val lastModBefore = hello.lastModifiedMillis
      Files.setLastModifiedTime(hello, FileTime.fromMillis(lastModBefore + 2.second.toMillis))

      Then("a Modified Diff should be published")
      val Modified(helloUpdated) = nextDiff()
      helloUpdated shouldBe hello

      val anotherFile = dir.resolve("anotherFile.txt").text = "There"
      val Modified(anotherFileCreated) = nextDiff()
      anotherFileCreated shouldBe anotherFile

      When("we remove a file which exists")
      hello.delete()

      Then("a Removed notification should be published")
      val Removed(helloDeleted) = nextDiff()
      helloDeleted shouldBe hello
    }
  }
}
